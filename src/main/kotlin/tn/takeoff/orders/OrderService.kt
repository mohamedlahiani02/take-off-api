package tn.takeoff.orders

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.orders.dto.*
import tn.takeoff.users.UserRepository
import java.time.Instant
import java.util.UUID

@Service
class OrderService(
    private val orderRepo: OrderRepository,
    private val userRepo: UserRepository,
) {

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
        val total = dto.items.sumOf { it.unitPriceDt.multiply(java.math.BigDecimal(it.qty)) }

        val order = Order(
            orderRef = ref,
            user = user,
            deliveryMethod = dto.deliveryMethod,
            deliveryAddress = dto.deliveryAddress,
            paymentMethod = dto.paymentMethod,
            totalDt = total,
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
