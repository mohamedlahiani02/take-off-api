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

    /**
     * The member holding this seat, or null for a guest the organiser named.
     * Exactly one of userId / guestName is set — enforced in the database.
     */
    @Column(name = "user_id")
    var userId: UUID? = null,

    /** Display name of a player who has no account. Never creates a user row. */
    @Column(name = "guest_name")
    var guestName: String? = null,

    /** Member who added this participant, when it was not an admin. */
    @Column(name = "added_by_user_id")
    var addedByUserId: UUID? = null,

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
