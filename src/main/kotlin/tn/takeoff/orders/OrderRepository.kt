package tn.takeoff.orders

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID>, OrderGateway {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    override fun findByIdForUpdate(id: UUID): Optional<Order>

    override fun findAllByUser_Id(userId: UUID, pageable: Pageable): Page<Order>
    override fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Order>
    override fun findByStatusOrderByCreatedAtDesc(status: OrderStatus, pageable: Pageable): Page<Order>

    @Query("SELECT COALESCE(SUM(o.totalDt), 0) FROM Order o WHERE o.status <> tn.takeoff.orders.OrderStatus.CANCELLED AND o.createdAt >= :from AND o.createdAt < :to")
    override fun sumRevenueBetween(from: Instant, to: Instant): BigDecimal?

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = tn.takeoff.orders.OrderStatus.PENDING")
    override fun countPendingOrders(): Long

    override fun countByCreatedAtBetween(from: Instant, to: Instant): Long
}
