package tn.takeoff.courts

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** PAID = tranche settled · PENDING = pay at club · COVERED = organizer paid it · WAIVED = written off. */
enum class PlayerPaymentStatus { PAID, PENDING, COVERED, WAIVED }
enum class PlayerPaymentMethod { D17, WALLET, CARD, CASH }

@Entity
@Table(name = "court_booking_players")
class CourtBookingPlayer(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "booking_id", nullable = false)
    val bookingId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "share_dt", precision = 10, scale = 3, nullable = false)
    var shareDt: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    var paymentStatus: PlayerPaymentStatus = PlayerPaymentStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    var paymentMethod: PlayerPaymentMethod? = null,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @Column(name = "no_show", nullable = false)
    var noShow: Boolean = false,

    @Column(name = "added_by_admin_id")
    var addedByAdminId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
