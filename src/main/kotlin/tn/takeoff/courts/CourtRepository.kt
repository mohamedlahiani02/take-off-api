package tn.takeoff.courts

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CourtGateway {
    fun findByActiveOrderByDisplayOrder(active: Boolean): List<Court>
}

interface CourtRepository : JpaRepository<Court, UUID>, CourtGateway {
    override fun findByActiveOrderByDisplayOrder(active: Boolean): List<Court>
}
