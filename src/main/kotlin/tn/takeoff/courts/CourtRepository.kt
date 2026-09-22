package tn.takeoff.courts

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface CourtGateway {
    fun findByActiveOrderByDisplayOrder(active: Boolean): List<Court>
}

interface CourtRepository : JpaRepository<Court, UUID>, CourtGateway {
    override fun findByActiveOrderByDisplayOrder(active: Boolean): List<Court>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Court c WHERE c.id = :id")
    fun findByIdForUpdate(id: UUID): Optional<Court>
}
