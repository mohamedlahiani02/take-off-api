package tn.takeoff.classes

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "class_types")
class ClassType(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var name: String,
    @Column var level: String? = null,
    @Column(name = "duration_min", nullable = false) var durationMin: Int = 50,
    @Column(columnDefinition = "text") var description: String? = null,
    @Column(name = "photo_url") var photoUrl: String? = null,
    @Column(name = "default_price_dt", precision = 10, scale = 3, nullable = false) var defaultPriceDt: BigDecimal = BigDecimal("35"),
    @Column(name = "display_order", nullable = false) var displayOrder: Int = 0,
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
)

enum class SessionStatus { SCHEDULED, CANCELLED, COMPLETED }

@Entity
@Table(name = "class_sessions")
class ClassSession(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "class_type_id", nullable = false) var classTypeId: UUID,
    @Column(name = "instructor_id") var instructorId: UUID? = null,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "duration_min", nullable = false) var durationMin: Int = 50,
    @Column(name = "max_spots", nullable = false) var maxSpots: Int = 8,
    @Column(name = "price_dt", precision = 10, scale = 3, nullable = false) var priceDt: BigDecimal = BigDecimal("35"),
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: SessionStatus = SessionStatus.SCHEDULED,
    @Column(name = "created_by_admin_id") var createdByAdminId: UUID? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)

enum class ClassBookingStatus {
    BOOKED, WAITLIST,
    // A spot was offered after promotion from the waitlist; the member has
    // until proposalExpiresAt to confirm (real payment/credit check happens
    // at confirmation, not here). DECLINED and EXPIRED are distinct from
    // CANCELLED on purpose — see V40.
    PROPOSED, DECLINED, EXPIRED,
    CANCELLED, ATTENDED, ABSENT, LATE_CANCEL,
}
enum class PaidWith { SINGLE, PACK, UNLIMITED, COMP, WAITLIST }

@Entity
@Table(name = "class_bookings")
class ClassBooking(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "session_id", nullable = false) var sessionId: UUID,
    @Column(name = "user_id") var userId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ClassBookingStatus = ClassBookingStatus.BOOKED,
    @Enumerated(EnumType.STRING) @Column(name = "paid_with", nullable = false) var paidWith: PaidWith = PaidWith.SINGLE,
    @Column(name = "user_pack_id") var userPackId: UUID? = null,
    @Column(name = "price_dt", precision = 10, scale = 3, nullable = false) var priceDt: BigDecimal = BigDecimal.ZERO,
    @Column(name = "created_by_admin_id") var createdByAdminId: UUID? = null,
    @Column(name = "waitlist_position") var waitlistPosition: Int? = null,
    // Set when the CLUB cancels an entire session less than 24h before it
    // starts and this booking was SINGLE-paid: policy denies an automatic
    // wallet refund that close to the session, so it is left flagged here for
    // an admin to refund by hand later (see AdminClassService.cancelSession /
    // pendingManualRefunds). Never cleared automatically — there is no
    // manual-refund endpoint yet, only the diagnostic list.
    @Column(name = "refund_pending", nullable = false) var refundPending: Boolean = false,
    // Set only while status == PROPOSED: the deadline to confirm before the
    // spot is auto-declined and offered to the next waitlisted member.
    @Column(name = "proposal_expires_at") var proposalExpiresAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)
