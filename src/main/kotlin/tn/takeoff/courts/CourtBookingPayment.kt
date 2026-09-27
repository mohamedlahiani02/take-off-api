package tn.takeoff.courts

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * How a guest's cash was actually collected. Deliberately has no WALLET value:
 * a guest paying at the desk has no account, so there is nothing to credit —
 * the type system rules out the one thing this must never become.
 */
enum class GuestPaymentMethod { CASH, CARD, D17 }

/**
 * Money collected against a booking that is not tied to a named seat.
 *
 * A court_booking_players row requires either a member or a named guest
 * (V35's constraint). A walk-in paying the remaining balance for two or three
 * friends who did not want accounts is real money with no such identity —
 * this table is where it lives instead of forcing fake player rows. It still
 * reserves `coveredSeats` so those seats stop being offered for booking, even
 * though who occupies them is unknown.
 */
@Entity
@Table(name = "court_booking_payments")
class CourtBookingPayment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "booking_id", nullable = false)
    val bookingId: UUID,

    @Column(name = "amount_dt", precision = 10, scale = 3, nullable = false)
    var amountDt: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var method: GuestPaymentMethod,

    @Column(name = "payer_name")
    var payerName: String? = null,

    /** How many of the booking's open seats this payment stands for (SHARE only). */
    @Column(name = "covered_seats", nullable = false)
    var coveredSeats: Int = 0,

    @Column
    var reference: String? = null,

    @Column(name = "collected_by_admin_id", nullable = false)
    var collectedByAdminId: UUID,

    @Column(nullable = false)
    var voided: Boolean = false,

    @Column(name = "voided_by_admin_id")
    var voidedByAdminId: UUID? = null,

    @Column(name = "voided_at")
    var voidedAt: Instant? = null,

    @Column(name = "void_reason")
    var voidReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

interface CourtBookingPaymentRepository : JpaRepository<CourtBookingPayment, UUID> {
    fun findByBookingIdOrderByCreatedAtDesc(bookingId: UUID): List<CourtBookingPayment>
    fun findByBookingIdInAndVoidedFalse(bookingIds: Collection<UUID>): List<CourtBookingPayment>
}
