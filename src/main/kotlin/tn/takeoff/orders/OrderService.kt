package tn.takeoff.orders

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.orders.dto.*
import tn.takeoff.products.ProductGateway
import tn.takeoff.products.ProductVariantRepository
import tn.takeoff.users.UserGateway
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class OrderService(
    private val orderRepo: OrderGateway,
    private val userRepo: UserGateway,
    private val variantRepo: ProductVariantRepository,
    private val walletService: WalletService,
    private val productGateway: ProductGateway,
    private val packTypes: tn.takeoff.packs.PackTypeRepository,
) {
    companion object {
        private val TIMBRE_FISCAL = java.math.BigDecimal("1.000")
        private val TIMBRE_THRESHOLD = java.math.BigDecimal("10.000")

    }

    @Transactional(readOnly = true)
    fun myOrders(userId: UUID, page: Int, size: Int): Page<OrderDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return orderRepo.findAllByUser_Id(userId, pageable).map { OrderDto.from(it) }
    }

    @Transactional(readOnly = true)
    fun getById(id: UUID, userId: UUID): OrderDto {
        val order = orderRepo.findById(id).orElseThrow { NotFoundException("order", id) }
        if (order.user?.id != userId) throw NotFoundException("order", id)
        return OrderDto.from(order)
    }

    @Transactional
    fun place(userId: UUID?, dto: PlaceOrderRequest): OrderDto {
        if (dto.paymentMethod == PaymentMethod.WALLET && userId == null) {
            throw BadRequestException("takeoff.order.wallet_requires_auth", "Wallet payment requires authentication")
        }

        val user = userId?.let { userRepo.findById(it).orElse(null) }
        val ref = generateRef()

        // Every line is priced from the catalogue. A client-supplied amount is
        // only ever compared against it, so a stale or tampered cart cannot set
        // its own price.
        val resolvedPrices = dto.items.map { item ->
            when {
                item.productId != null -> {
                    val product = productGateway.findById(item.productId).orElseThrow {
                        BadRequestException("takeoff.product.not_found", "Product not found: ${item.productId}")
                    }
                    if (!product.isActive)
                        throw BadRequestException("takeoff.product.inactive", "Product '${item.productName}' is not available")
                    product.priceDt
                }
                item.packTypeId != null -> {
                    val pack = packTypes.findById(item.packTypeId).orElseThrow {
                        BadRequestException("takeoff.pack.not_found", "Pack not found: ${item.packTypeId}")
                    }
                    if (!pack.active)
                        throw BadRequestException("takeoff.pack.inactive", "Pack '${item.productName}' is not available")
                    pack.priceDt
                }
                else -> throw BadRequestException(
                    "takeoff.order.unpriced_item",
                    "'${item.productName}' does not reference anything the club sells",
                )
            }
        }


        val subtotal = dto.items.zip(resolvedPrices)
            .sumOf { (item, price) -> price.multiply(BigDecimal(item.qty)) }

        val hasPhysical = dto.items.any { it.productId != null }
        val timbreFiscal = if (hasPhysical && subtotal >= TIMBRE_THRESHOLD) TIMBRE_FISCAL else BigDecimal.ZERO

        val deliveryFee = when (dto.deliveryMethod) {
            DeliveryMethod.PICKUP -> BigDecimal.ZERO
            DeliveryMethod.DELIVER -> {
                val city = (dto.deliveryAddress?.get("city") ?: "").trim().lowercase()
                if (city.isEmpty() || city == "sfax") BigDecimal("7.000") else BigDecimal("15.000")
            }
        }

        val total = subtotal.add(timbreFiscal).add(deliveryFee)

        dto.items.forEach { item ->
            val pid = item.productId ?: return@forEach
            val variants = variantRepo.findByProductIdForUpdate(pid)
            val variant = if (item.size != null)
                variants.firstOrNull { it.size.equals(item.size, ignoreCase = true) }
            else
                variants.firstOrNull()

            if (variants.isNotEmpty() && variant == null) {
                throw BadRequestException("takeoff.product.invalid_size", "Invalid or missing size for '${item.productName}'")
            }

            if (variant != null) {
                if (variant.stock < item.qty)
                    throw BadRequestException(
                        "takeoff.product.out_of_stock",
                        "Not enough stock for '${item.productName}' size ${item.size ?: "default"} (available: ${variant.stock})"
                    )
                variant.stock -= item.qty
                variantRepo.save(variant)
            } else {
                val product = productGateway.findByIdForUpdate(pid).orElse(null) ?: return@forEach
                if (product.stock < item.qty)
                    throw BadRequestException(
                        "takeoff.product.out_of_stock",
                        "Not enough stock for '${item.productName}' (available: ${product.stock})"
                    )
                product.stock -= item.qty
                productGateway.save(product)
            }
        }

        val order = Order(
            orderRef = ref,
            user = user,
            deliveryMethod = dto.deliveryMethod,
            deliveryAddress = dto.deliveryAddress,
            paymentMethod = dto.paymentMethod,
            totalDt = total,
            timbreFiscalDt = timbreFiscal,
            deliveryFeeDt = deliveryFee,
            discountCode = dto.discountCode,
            discountAmountDt = BigDecimal.ZERO,
            contact = dto.contact,
        )
        dto.items.zip(resolvedPrices).forEach { (item, price) ->
            order.items.add(OrderItem(
                order = order,
                productId = item.productId,
                productName = item.productName,
                qty = item.qty,
                size = item.size,
                unitPriceDt = price,
            ))
        }

        if (dto.paymentMethod == PaymentMethod.WALLET && userId != null) {
            walletService.apply(
                userId = userId, delta = total.negate(),
                type = WalletEntryType.PAYMENT, reason = "order_${ref}",
                refType = "order", refId = ref,
            )
        }

        if (dto.paymentMethod == PaymentMethod.WALLET) {
            order.status = OrderStatus.CONFIRMED
            order.updatedAt = Instant.now()
        }

        return OrderDto.from(orderRepo.save(order))
    }

    @Transactional
    fun cancelMine(orderId: UUID, userId: UUID): OrderDto {
        val order = orderRepo.findByIdForUpdate(orderId).orElseThrow { NotFoundException("order", orderId) }
        if (order.user?.id != userId) throw NotFoundException("order", orderId)
        if (order.status !in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED))
            throw BadRequestException("takeoff.order.not_cancellable", "Order cannot be cancelled in status ${order.status}")

        doCancel(order)
        return OrderDto.from(order)
    }

    @Transactional
    fun updateStatus(orderId: UUID, action: String): OrderDto {
        val order = orderRepo.findByIdForUpdate(orderId).orElseThrow { NotFoundException("order", orderId) }
        if (action.lowercase() == "cancel") {
            if (!OrderTransitions.canCancel(order.status))
                throw BadRequestException(
                    "takeoff.order.not_cancellable",
                    "Order cannot be cancelled in status ${order.status}",
                )
            doCancel(order)
            return OrderDto.from(order)
        }
        val target = OrderTransitions.statusForAction(action)
            ?: throw BadRequestException("takeoff.order.invalid_action", "Unknown action: $action")
        if (!OrderTransitions.allows(order.status, target))
            throw BadRequestException(
                "takeoff.order.invalid_transition",
                "Cannot move an order from ${order.status} to $target",
            )
        order.status = target
        order.updatedAt = Instant.now()
        return OrderDto.from(orderRepo.save(order))
    }

    private fun doCancel(order: Order) {
        // Guard against a second refund/restock for the same order, however the order got
        // back into a cancellable state. The ledger identity is the refund identity.
        if (order.status == OrderStatus.CANCELLED) return

        val uid = order.user?.id
        if (order.paymentMethod == PaymentMethod.WALLET && uid != null) {
            walletService.applyOnce(
                userId = uid, delta = order.totalDt,
                type = WalletEntryType.REFUND, reason = "order_cancel_${order.orderRef}",
                refType = "order", refId = order.orderRef,
            )
        }

        order.items.forEach { item ->
            val pid = item.productId ?: return@forEach
            val variants = variantRepo.findByProductIdForUpdate(pid)
            val variant = if (item.size != null)
                variants.firstOrNull { it.size.equals(item.size, ignoreCase = true) }
            else
                variants.firstOrNull()
            if (variant != null) {
                variant.stock += item.qty
                variantRepo.save(variant)
            } else {
                productGateway.findByIdForUpdate(pid).ifPresent { product ->
                    product.stock += item.qty
                    productGateway.save(product)
                }
            }
        }

        order.status = OrderStatus.CANCELLED
        order.updatedAt = Instant.now()
        orderRepo.save(order)
    }

    private fun generateRef(): String =
        "TKO-" + java.util.UUID.randomUUID().toString().replace("-", "").uppercase().take(10)
}
