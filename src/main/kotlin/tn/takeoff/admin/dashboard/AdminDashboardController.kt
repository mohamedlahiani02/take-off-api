package tn.takeoff.admin.dashboard

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tn.takeoff.admin.expenses.ExpenseGateway
import tn.takeoff.orders.OrderGateway
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class DashboardStats(
    val revenueMtd: BigDecimal,
    val expensesMtd: BigDecimal,
    val netCashFlow: BigDecimal,
    val pendingOrders: Long,
    val ordersToday: Long,
)

data class RevenueDay(
    val date: String,
    val revenue: BigDecimal,
    val orders: Long,
)

data class RevenueReport(
    val days: List<RevenueDay>,
    val totalRevenue: BigDecimal,
    val totalOrders: Long,
)

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
class AdminDashboardController(
    private val orderGateway: OrderGateway,
    private val expenseGateway: ExpenseGateway,
) {

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    fun dashboard(): DashboardStats {
        val now = Instant.now()
        val monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()

        val revenueMtd = orderGateway.sumRevenueBetween(monthStart, now) ?: BigDecimal.ZERO
        val expensesMtd = expenseGateway.findByDateBetweenOrderByDateDesc(
            LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1),
            LocalDate.now(ZoneOffset.UTC),
        ).fold(BigDecimal.ZERO) { acc, e -> acc + e.amountDt }

        return DashboardStats(
            revenueMtd = revenueMtd,
            expensesMtd = expensesMtd,
            netCashFlow = revenueMtd - expensesMtd,
            pendingOrders = orderGateway.countPendingOrders(),
            ordersToday = orderGateway.countByCreatedAtBetween(todayStart, now),
        )
    }

    @GetMapping("/reports/revenue")
    @Transactional(readOnly = true)
    fun revenueReport(@RequestParam(defaultValue = "14") days: Int): RevenueReport {
        val today = LocalDate.now(ZoneOffset.UTC)
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val result = mutableListOf<RevenueDay>()

        for (i in (days - 1) downTo 0) {
            val day = today.minusDays(i.toLong())
            val from = day.atStartOfDay(ZoneOffset.UTC).toInstant()
            val to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            val rev = orderGateway.sumRevenueBetween(from, to) ?: BigDecimal.ZERO
            val cnt = orderGateway.countByCreatedAtBetween(from, to)
            result.add(RevenueDay(date = day.format(fmt), revenue = rev, orders = cnt))
        }

        return RevenueReport(
            days = result,
            totalRevenue = result.fold(BigDecimal.ZERO) { acc, d -> acc + d.revenue },
            totalOrders = result.sumOf { it.orders },
        )
    }
}
