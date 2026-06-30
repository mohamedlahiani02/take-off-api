package tn.takeoff.orders

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID>, OrderGateway {
    override fun findAllByUser_Id(userId: UUID, pageable: Pageable): Page<Order>
    override fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Order>
}
