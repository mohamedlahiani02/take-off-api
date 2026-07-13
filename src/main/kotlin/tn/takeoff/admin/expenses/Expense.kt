package tn.takeoff.admin.expenses

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "expenses")
class Expense(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var date: LocalDate,

    @Column(nullable = false)
    var category: String,

    @Column(name = "amount_dt", precision = 10, scale = 3, nullable = false)
    var amountDt: BigDecimal,

    var supplier: String? = null,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null,

    @Column(name = "created_by")
    val createdBy: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
