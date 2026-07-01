package tn.takeoff.orders

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.orders.dto.*
import tn.takeoff.products.ProductVariantRepository
import tn.takeoff.users.UserGateway
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import java.time.Instant
import java.util.UUID

@Service
class OrderService(
    private val orderRepo: OrderGateway,
    private val userRepo: UserGateway,
    private val variantRepo: ProductVariantRepository,
    private val walletService: WalletService,
) {
    companion object {
        private val TIMBRE_FISCAL = java.math.BigDecimal("1.000")
        private val TIMBRE_THRESHOLD = java.math.BigDecimal("10.000")
    }

    fun myOrders(userId: UUID, page: Int, size: Int): Page<OrderDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return orderRepo.findAllByUser_Id(userId, pageable).map { OrderDto.from(it) }
    }

    fun getById(id: UUID, userId: UUID): OrderDto {
        val order = orderRepo.findById(id).orElseThrow { NotFoundException("order", id) }
        if (order.user?.id != userId) throw NotFoundException("order", id)
        return OrderDto.from(order)
    }

    @Transactional
    fun place(userId: UUID?, dto: PlaceOrderRequest): OrderDto {
        val user = userId?.let { userRepo.findById(it).orElse(null) }
        val ref = generateRef()
        val subtotal = dto.items.sumOf { it.unitPriceDt.multiply(java.math.BigDecimal(it.qty)) }

        // Timbre fiscal: 1 DT for orders with physical products >= 10 DT
        val hasPhysical = dto.items.any { it.productId != null }
        val timbreFiscal = if (hasPhysical && subtotal >= TIMBRE_THRESHOLD) TIMBRE_FISCAL else java.math.BigDecimal.ZERO

        val deliveryFee = dto.deliveryFeeDt.coerceAtLeast(java.math.BigDecimal.ZERO)
        val total = subtotal.add(timbreFiscal).add(deliveryFee)

        // Stock check and decrement per variant (pessimistic locked)
        dto.items.forEach { item ->
            val pid = item.productId ?: return@forEach
            val variants = variantRepo.findByProductIdForUpdate(pid)
            val variant = if (item.size != null)
                variants.firstOrNull { it.size.equals(item.size, ignoreCase = true) }
            else
                variants.firstOrNull()

            if (variant != null) {
                if (variant.stock < item.qty)
                    throw BadRequestException(
                        "takeoff.product.out_of_stock",
                        "Not enough stock for '${item.productName}' size ${item.size ?: "default"} (available: ${variant.stock})"
                    )
                variant.stock -= item.qty
                variantRepo.save(variant)
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
            discountAmountDt = java.math.BigDecimal.ZERO,
            contact = dto.contact,
        )
        dto.items.forEach { item ->
            order.items.add(OrderItem(
                order = order,
                productId = item.productId,
                productName = item.productName,
                qty = item.qty,
                size = item.size,
                unitPriceDt = item.unitPriceDt,
            ))
        }

        // Wallet payment: deduct immediately (pessimistic-locked inside walletService)
        if (dto.paymentMethod == PaymentMethod.WALLET && userId != null) {
            walletService.apply(
                userId = userId, delta = total.negate(),
                type = WalletEntryType.PAYMENT, reason = "order_${ref}",
                refType = "order", refId = ref,
            )
        }

        return OrderDto.from(orderRepo.save(order))
    }

    @Transactional
    fun cancelMine(orderId: UUID, userId: UUID): OrderDto {
        val order = orderRepo.findById(orderId).orElseThrow { NotFoundException("order", orderId) }
        if (order.user?.id != userId) throw NotFoundException("order", orderId)
        if (order.status !in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED))
            throw BadRequestException("takeoff.order.not_cancellable", "Order cannot be cancelled in status ${order.status}")

        // Refund to wallet if paid via wallet
        if (order.paymentMethod == PaymentMethod.WALLET && userId != null) {
            walletService.apply(
                userId = userId, delta = order.totalDt,
                type = WalletEntryType.REFUND, reason = "order_cancel_${order.orderRef}",
                refType = "order", refId = order.orderRef,
            )
        }

        // Restore stock (pessimistic locked)
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
            }
        }

        order.status = OrderStatus.CANCELLED
        order.updatedAt = Instant.now()
        return OrderDto.from(orderRepo.save(order))
    }

    @Transactional
    fun updateStatus(orderId: UUID, action: String): OrderDto {
        val order = orderRepo.findById(orderId).orElseThrow { NotFoundException("order", orderId) }
        order.status = when (action.lowercase()) {
            "confirm" -> OrderStatus.CONFIRMED
            "prepare" -> OrderStatus.PREPARING
            "ship" -> OrderStatus.SHIPPED
            "deliver" -> OrderStatus.DELIVERED
            "pickup_ready" -> OrderStatus.PICKUP_READY
            "picked_up" -> OrderStatus.PICKED_UP
            "cancel" -> OrderStatus.CANCELLED
            else -> throw BadRequestException("takeoff.order.invalid_action", "Unknown action: $action")
        }
        order.updatedAt = Instant.now()
        return OrderDto.from(orderRepo.save(order))
    }

    private fun generateRef(): String {
        val suffix = (100000..999999).random()
        return "TKO-$suffix"
    }
}
