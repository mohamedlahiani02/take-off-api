package tn.takeoff.courts

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface CourtBookingRepository : JpaRepository<CourtBooking, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM CourtBooking b WHERE b.id = :id")
    fun findByIdForUpdate(id: UUID): Optional<CourtBooking>

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
