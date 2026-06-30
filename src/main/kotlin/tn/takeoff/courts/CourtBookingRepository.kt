package tn.takeoff.courts

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface CourtBookingRepository : JpaRepository<CourtBooking, UUID> {

    // Calendar window: all bookings starting within [from, to).
    fun findByStartsAtGreaterThanEqualAndStartsAtLessThan(from: Instant, to: Instant): List<CourtBooking>

    // Overlap detection for one court: starts before the new end AND ends after the new start.
    fun findByCourtIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
        courtId: UUID,
        status: BookingStatus,
        endsAt: Instant,
        startsAt: Instant,
    ): List<CourtBooking>

    fun findByUserIdOrderByStartsAtDesc(userId: UUID): List<CourtBooking>
}
