package tn.takeoff.admin.expenses

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface ExpenseGateway {
    fun save(expense: Expense): Expense
    fun findById(id: UUID): Optional<Expense>
    fun delete(expense: Expense)
    fun findAllByOrderByDateDescCreatedAtDesc(pageable: Pageable): Page<Expense>
    fun findByDateBetweenOrderByDateDesc(from: LocalDate, to: LocalDate): List<Expense>
}
