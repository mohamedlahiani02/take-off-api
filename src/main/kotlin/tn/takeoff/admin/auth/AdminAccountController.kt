package tn.takeoff.admin.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.ConflictException
import tn.takeoff.common.errors.NotFoundException
import java.time.Instant
import java.util.UUID

// ── DTOs ────────────────────────────────────────────────────────────────────

data class AdminAccountDto(
    val id: UUID,
    val name: String,
    val email: String,
    val role: AdminRole,
    val active: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(a: Admin) = AdminAccountDto(a.id, a.name, a.email, a.role, a.active, a.createdAt)
    }
}

data class CreateAdminRequest(
    @field:NotBlank val name: String,
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, message = "Password must be at least 8 characters") val password: String,
    @field:NotNull val role: AdminRole,
)

data class SetActiveRequest(@field:NotNull val active: Boolean)

// ── Service ─────────────────────────────────────────────────────────────────

@Service
class AdminAccountService(
    private val admins: AdminGateway,
    private val passwordEncoder: PasswordEncoder,
) {
    fun list(): List<AdminAccountDto> = admins.findAllByOrderByCreatedAtAsc().map(AdminAccountDto::from)

    fun create(req: CreateAdminRequest): AdminAccountDto {
        val email = req.email.trim().lowercase()
        if (admins.existsByEmail(email))
            throw ConflictException("takeoff.admin.email_taken", "An admin with that email already exists")
        val saved = admins.save(
            Admin(
                email = email,
                passwordHash = passwordEncoder.encode(req.password),
                name = req.name.trim(),
                role = req.role,
            )
        )
        return AdminAccountDto.from(saved)
    }

    fun setActive(id: UUID, active: Boolean, actingAdminId: UUID): AdminAccountDto {
        val admin = admins.findById(id).orElseThrow { NotFoundException("admin", id) }
        if (!active) {
            // You can't lock yourself out, and the club must always keep at
            // least one active super admin who can manage the others.
            if (admin.id == actingAdminId)
                throw BadRequestException("takeoff.admin.self_deactivate", "You cannot deactivate your own account")
            if (admin.role == AdminRole.SUPER_ADMIN && admin.active &&
                admins.countByRoleAndActive(AdminRole.SUPER_ADMIN, true) <= 1
            ) {
                throw BadRequestException(
                    "takeoff.admin.last_super_admin",
                    "Cannot deactivate the last active super admin",
                )
            }
        }
        admin.active = active
        admin.updatedAt = Instant.now()
        return AdminAccountDto.from(admins.save(admin))
    }
}

// ── Controller ──────────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AdminAccountController(private val service: AdminAccountService) {

    @GetMapping
    fun list(): List<AdminAccountDto> = service.list()

    @PostMapping
    fun create(@Valid @RequestBody req: CreateAdminRequest): AdminAccountDto = service.create(req)

    @PatchMapping("/{id}/active")
    fun setActive(
        @PathVariable id: UUID,
        @Valid @RequestBody req: SetActiveRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): AdminAccountDto = service.setActive(id, req.active, admin.adminId)
}
