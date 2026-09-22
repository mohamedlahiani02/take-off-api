package tn.takeoff.orders

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface OrderGateway {
    fun findById(id: UUID): Optional<Order>
    fun findByIdForUpdate(id: UUID): Optional<Order>
    fun findAllByUser_Id(userId: UUID, pageable: Pageable): Page<Order>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Order>
    fun findByStatusOrderByCreatedAtDesc(status: OrderStatus, pageable: Pageable): Page<Order>
    fun save(order: Order): Order
    fun sumRevenueBetween(from: Instant, to: Instant): BigDecimal?
    fun countPendingOrders(): Long
    fun countByCreatedAtBetween(from: Instant, to: Instant): Long
}
