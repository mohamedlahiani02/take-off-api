package tn.takeoff.orders

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Order>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Order>
}
