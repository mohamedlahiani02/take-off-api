package tn.takeoff.orders

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import tn.takeoff.users.User
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class OrderStatus {
    PENDING, CONFIRMED, PREPARING, SHIPPED, PICKUP_READY, DELIVERED, PICKED_UP, CANCELLED
}

enum class DeliveryMethod { PICKUP, DELIVER }

enum class PaymentMethod { COD, D17, WALLET, CARD }

@Entity
@Table(name = "orders")
class Order(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "order_ref", unique = true, nullable = false)
    val orderRef: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    val deliveryMethod: DeliveryMethod,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_address", columnDefinition = "jsonb")
    val deliveryAddress: Map<String, String>? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    val paymentMethod: PaymentMethod,

    @Column(name = "total_dt", precision = 10, scale = 3, nullable = false)
    val totalDt: BigDecimal,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact", columnDefinition = "jsonb")
    val contact: Map<String, String>,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf(),
)

@Entity
@Table(name = "order_items")
class OrderItem(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    @Column(name = "product_id")
    val productId: UUID,

    @Column(name = "product_name", nullable = false)
    val productName: String,

    var qty: Int,

    var size: String? = null,

    @Column(name = "unit_price_dt", precision = 10, scale = 3, nullable = false)
    val unitPriceDt: BigDecimal,
)
