package tn.takeoff.admin.packs

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.packs.PackType
import tn.takeoff.packs.UserPack
import tn.takeoff.packs.UserPackStatus
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/packs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','RECEPTION')")
class AdminPackController(private val service: AdminPackService) {

    @GetMapping("/types") fun listTypes(): List<PackType> = service.listTypes()

    @PostMapping("/types")
    fun createType(@Valid @RequestBody req: PackTypeRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): PackType =
        service.createType(req, a.adminId)

    @PutMapping("/types/{id}")
    fun updateType(@PathVariable id: UUID, @Valid @RequestBody req: PackTypeRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): PackType =
        service.updateType(id, req, a.adminId)

    @GetMapping("/user-packs") fun activeUserPacks(): List<UserPack> = service.activeUserPacks()

    @GetMapping("/user-packs/by-user/{userId}")
    fun userPacks(@PathVariable userId: UUID): List<UserPack> = service.userPacksFor(userId)

    @PostMapping("/assign")
    fun assign(@Valid @RequestBody req: AssignPackRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): UserPack =
        service.assign(req, a.adminId)

    @PostMapping("/user-packs/{id}/extend")
    fun extend(@PathVariable id: UUID, @Valid @RequestBody req: ExtendRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): UserPack =
        service.extend(id, req.newExpiresAt, req.reason, a.adminId)

    @PostMapping("/user-packs/{id}/credits")
    fun adjust(@PathVariable id: UUID, @Valid @RequestBody req: CreditAdjustRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): UserPack =
        service.adjustCredits(id, req.delta, req.reason, a.adminId)

    @PostMapping("/user-packs/{id}/freeze")
    fun freeze(@PathVariable id: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims): UserPack =
        service.setStatus(id, UserPackStatus.FROZEN, a.adminId)

    @PostMapping("/user-packs/{id}/cancel")
    fun cancel(@PathVariable id: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims): UserPack =
        service.setStatus(id, UserPackStatus.CANCELLED, a.adminId)
}
