package tn.takeoff.classes

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface ClassTypeRepository : JpaRepository<ClassType, UUID> {
    fun findAllByOrderByDisplayOrder(): List<ClassType>
    fun findByActiveOrderByDisplayOrder(active: Boolean): List<ClassType>
}

interface ClassSessionRepository : JpaRepository<ClassSession, UUID> {
    fun findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(from: Instant, to: Instant): List<ClassSession>
}

interface ClassBookingRepository : JpaRepository<ClassBooking, UUID> {
    fun findBySessionId(sessionId: UUID): List<ClassBooking>
    fun countBySessionIdAndStatus(sessionId: UUID, status: ClassBookingStatus): Long
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<ClassBooking>
}
