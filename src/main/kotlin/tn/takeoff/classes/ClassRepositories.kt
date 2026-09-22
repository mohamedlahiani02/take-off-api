package tn.takeoff.classes

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface ClassTypeRepository : JpaRepository<ClassType, UUID> {
    fun findAllByOrderByDisplayOrder(): List<ClassType>
    fun findByActiveOrderByDisplayOrder(active: Boolean): List<ClassType>
}

interface ClassSessionRepository : JpaRepository<ClassSession, UUID> {
    fun findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(from: Instant, to: Instant): List<ClassSession>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ClassSession s WHERE s.id = :id")
    fun findByIdForUpdate(id: UUID): Optional<ClassSession>
}

interface ClassBookingRepository : JpaRepository<ClassBooking, UUID> {
    fun findBySessionId(sessionId: UUID): List<ClassBooking>
    fun countBySessionIdAndStatus(sessionId: UUID, status: ClassBookingStatus): Long
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<ClassBooking>
}
