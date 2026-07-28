package tn.takeoff.courts

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CourtBookingPlayerRepository : JpaRepository<CourtBookingPlayer, UUID> {

    fun findByBookingId(bookingId: UUID): List<CourtBookingPlayer>

    fun findByBookingIdIn(bookingIds: Collection<UUID>): List<CourtBookingPlayer>

    fun existsByBookingIdAndUserId(bookingId: UUID, userId: UUID): Boolean

    // Receivables: everything a user still owes (US-3.4 reads this per user).
    fun findByUserIdAndPaymentStatus(userId: UUID, status: PlayerPaymentStatus): List<CourtBookingPlayer>

    fun findByPaymentStatus(status: PlayerPaymentStatus): List<CourtBookingPlayer>
}
