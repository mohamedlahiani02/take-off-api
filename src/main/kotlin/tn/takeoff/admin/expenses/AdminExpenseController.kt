package tn.takeoff.admin.expenses

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import tn.takeoff.common.errors.NotFoundException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ExpenseRequest(
    @field:NotNull val date: LocalDate,
    @field:NotBlank val category: String,
    @field:NotNull @field:DecimalMin("0.001") val amountDt: BigDecimal,
    val supplier: String? = null,
    val notes: String? = null,
)

data class ExpenseDto(
    val id: UUID,
    val date: LocalDate,
    val category: String,
    val amountDt: BigDecimal,
    val supplier: String?,
    val notes: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(e: Expense) = ExpenseDto(e.id, e.date, e.category, e.amountDt, e.supplier, e.notes, e.createdAt)
    }
}

@RestController
@RequestMapping("/api/v1/admin/expenses")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
class AdminExpenseController(private val expenseRepo: ExpenseGateway) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<ExpenseDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date", "createdAt"))
        return expenseRepo.findAllByOrderByDateDescCreatedAtDesc(pageable).map { ExpenseDto.from(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody dto: ExpenseRequest): ExpenseDto {
        val expense = Expense(
            date = dto.date,
            category = dto.category.trim(),
            amountDt = dto.amountDt,
            supplier = dto.supplier?.trim(),
            notes = dto.notes?.trim(),
        )
        return ExpenseDto.from(expenseRepo.save(expense))
    }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody dto: ExpenseRequest): ExpenseDto {
        val expense = expenseRepo.findById(id).orElseThrow { NotFoundException("expense", id) }
        expense.date = dto.date
        expense.category = dto.category.trim()
        expense.amountDt = dto.amountDt
        expense.supplier = dto.supplier?.trim()
        expense.notes = dto.notes?.trim()
        return ExpenseDto.from(expenseRepo.save(expense))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        val expense = expenseRepo.findById(id).orElseThrow { NotFoundException("expense", id) }
        expenseRepo.delete(expense)
    }
}
