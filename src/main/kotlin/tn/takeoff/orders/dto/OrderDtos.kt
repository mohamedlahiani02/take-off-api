package tn.takeoff.orders.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import tn.takeoff.orders.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CartItemInput(
    @field:NotNull val productId: UUID,
    val productName: String,
    val qty: Int = 1,
    val size: String? = null,
    val unitPriceDt: BigDecimal,
)

data class PlaceOrderRequest(
    @field:NotNull val deliveryMethod: DeliveryMethod,
    val deliveryAddress: Map<String, String>? = null,
    @field:NotNull val paymentMethod: PaymentMethod,
    val contact: Map<String, String> = emptyMap(),
    @field:NotEmpty val items: List<CartItemInput>,
)

data class OrderItemDto(
    val id: UUID,
    val productId: UUID,
    val productName: String,
    val qty: Int,
    val size: String?,
    val unitPriceDt: BigDecimal,
)

data class OrderDto(
    val id: UUID,
    val orderRef: String,
    val status: OrderStatus,
    val deliveryMethod: DeliveryMethod,
    val deliveryAddress: Map<String, String>?,
    val paymentMethod: PaymentMethod,
    val totalDt: BigDecimal,
    val contact: Map<String, String>,
    val items: List<OrderItemDto>,
    val createdAt: Instant,
) {
    companion object {
        fun from(o: Order) = OrderDto(
            id = o.id,
            orderRef = o.orderRef,
            status = o.status,
            deliveryMethod = o.deliveryMethod,
            deliveryAddress = o.deliveryAddress,
            paymentMethod = o.paymentMethod,
            totalDt = o.totalDt,
            contact = o.contact,
            items = o.items.map { item ->
                OrderItemDto(item.id, item.productId, item.productName, item.qty, item.size, item.unitPriceDt)
            },
            createdAt = o.createdAt,
        )
    }
}

data class UpdateOrderStatusRequest(
    @field:NotNull val action: String,
)
