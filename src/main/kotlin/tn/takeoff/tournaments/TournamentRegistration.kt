package tn.takeoff.tournaments

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class RegPaymentStatus { PAID, PAY_AT_CLUB, PENDING, REFUNDED }
enum class RegStatus { CONFIRMED, PENDING, WAITLIST, REJECTED, CANCELLED }

@Entity
@Table(name = "tournament_registrations")
class TournamentRegistration(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tournament_id", nullable = false)
    var tournamentId: UUID,

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "category_label")
    var categoryLabel: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var answers: Map<String, Any> = emptyMap(),

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    var paymentStatus: RegPaymentStatus = RegPaymentStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RegStatus = RegStatus.PENDING,

    @Column(name = "promo_code_id")
    var promoCodeId: UUID? = null,

    @Column(name = "amount_paid_dt", precision = 10, scale = 3, nullable = false)
    var amountPaidDt: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_by_admin_id")
    var createdByAdminId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
