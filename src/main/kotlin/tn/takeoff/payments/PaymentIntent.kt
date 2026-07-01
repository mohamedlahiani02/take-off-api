package tn.takeoff.payments

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payment_intents")
class PaymentIntent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id")
    val userId: UUID? = null,

    @Column(name = "ref_type", nullable = false, length = 30)
    val refType: String,

    @Column(name = "ref_id", nullable = false, length = 100)
    val refId: String,

    @Column(name = "amount_dt", precision = 10, scale = 3, nullable = false)
    val amountDt: BigDecimal,

    @Column(name = "konnect_pay_ref", length = 200)
    var konnectPayRef: String? = null,

    @Column(name = "konnect_pay_url", columnDefinition = "TEXT")
    var konnectPayUrl: String? = null,

    @Column(nullable = false, length = 20)
    var status: String = "PENDING",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null,
)
