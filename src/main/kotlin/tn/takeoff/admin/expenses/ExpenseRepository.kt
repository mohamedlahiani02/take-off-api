package tn.takeoff.admin.expenses

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface ExpenseRepository : JpaRepository<Expense, UUID>, ExpenseGateway {
    override fun findAllByOrderByDateDescCreatedAtDesc(pageable: Pageable): Page<Expense>
    override fun findByDateBetweenOrderByDateDesc(from: LocalDate, to: LocalDate): List<Expense>
}
