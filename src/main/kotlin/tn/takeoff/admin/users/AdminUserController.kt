package tn.takeoff.admin.users

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.users.AccountStatus
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','RECEPTION')")
class AdminUserController(private val service: AdminUserService) {

    /** B-01: instant search by phone / name / email. */
    @GetMapping("/search")
    fun search(@RequestParam q: String): List<UserSummaryDto> = service.search(q)

    /** B-12: list with optional status filter. */
    @GetMapping
    fun list(
        @RequestParam(required = false) status: AccountStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<UserSummaryDto> = service.list(status, page, size)

    /** B-04: full profile. */
    @GetMapping("/{id}")
    fun profile(@PathVariable id: UUID): UserProfileDto = service.getProfile(id)

    /** B-02: create a ghost user. */
    @PostMapping
    fun createGhost(
        @Valid @RequestBody req: CreateGhostRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): UserSummaryDto = service.createGhost(req, admin.adminId)

    /** B-05: edit name / phone / email. */
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateUserRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): UserSummaryDto = service.update(id, req, admin.adminId)

    /** B-06: credit wallet. */
    @PostMapping("/{id}/wallet/credit")
    fun credit(
        @PathVariable id: UUID,
        @Valid @RequestBody req: WalletAdjustRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): UserSummaryDto = service.adjustWallet(id, req.amountDt, req.reason, credit = true, adminId = admin.adminId)

    /** B-06: debit wallet. */
    @PostMapping("/{id}/wallet/debit")
    fun debit(
        @PathVariable id: UUID,
        @Valid @RequestBody req: WalletAdjustRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): UserSummaryDto = service.adjustWallet(id, req.amountDt, req.reason, credit = false, adminId = admin.adminId)

    /** B-10: block. */
    @PostMapping("/{id}/block")
    fun block(
        @PathVariable id: UUID,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): UserSummaryDto = service.setBlocked(id, blocked = true, adminId = admin.adminId)

    /** B-10: unblock. */
    @PostMapping("/{id}/unblock")
    fun unblock(
        @PathVariable id: UUID,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): UserSummaryDto = service.setBlocked(id, blocked = false, adminId = admin.adminId)

    /** B-11: soft-delete (GDPR). */
    @DeleteMapping("/{id}")
    fun softDelete(
        @PathVariable id: UUID,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ) = service.softDelete(id, admin.adminId)
}
