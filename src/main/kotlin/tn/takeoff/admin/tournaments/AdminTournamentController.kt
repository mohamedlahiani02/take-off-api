package tn.takeoff.admin.tournaments

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.tournaments.Tournament
import tn.takeoff.tournaments.TournamentField
import tn.takeoff.tournaments.TournamentPricing
import tn.takeoff.tournaments.TournamentPromoCode
import tn.takeoff.tournaments.TournamentRegistration
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/tournaments")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
class AdminTournamentController(private val service: AdminTournamentService) {

    @GetMapping fun list(): List<Tournament> = service.list()
    @GetMapping("/{id}") fun detail(@PathVariable id: UUID): TournamentDetail = service.detail(id)

    @PostMapping
    fun create(@Valid @RequestBody req: TournamentRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): Tournament =
        service.create(req, a.adminId)

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody req: TournamentRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): Tournament =
        service.update(id, req, a.adminId)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims) = service.delete(id, a.adminId)

    @PostMapping("/{id}/status")
    fun status(@PathVariable id: UUID, @Valid @RequestBody req: StatusRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): Tournament =
        service.setStatus(id, req.status, a.adminId)

    @PostMapping("/{id}/duplicate")
    fun duplicate(@PathVariable id: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims): Tournament =
        service.duplicate(id, a.adminId)

    // form builder
    @PostMapping("/{id}/fields")
    fun addField(@PathVariable id: UUID, @Valid @RequestBody req: FieldRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): TournamentField =
        service.addField(id, req, a.adminId)

    @PutMapping("/fields/{fieldId}")
    fun updateField(@PathVariable fieldId: UUID, @Valid @RequestBody req: FieldRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): TournamentField =
        service.updateField(fieldId, req, a.adminId)

    @DeleteMapping("/fields/{fieldId}")
    fun deleteField(@PathVariable fieldId: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims) = service.deleteField(fieldId, a.adminId)

    // pricing
    @PutMapping("/{id}/pricing")
    fun setPricing(@PathVariable id: UUID, @RequestBody list: List<PricingRequest>, @AuthenticationPrincipal a: JwtService.AdminClaims): List<TournamentPricing> =
        service.setPricing(id, list, a.adminId)

    // promo
    @PostMapping("/{id}/promo")
    fun addPromo(@PathVariable id: UUID, @Valid @RequestBody req: PromoCodeRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): TournamentPromoCode =
        service.addPromo(id, req, a.adminId)

    @DeleteMapping("/promo/{promoId}")
    fun deletePromo(@PathVariable promoId: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims) = service.deletePromo(promoId, a.adminId)

    // registrations
    @GetMapping("/{id}/registrations")
    fun registrations(@PathVariable id: UUID): List<TournamentRegistration> = service.listRegistrations(id)

    @PostMapping("/{id}/registrations")
    fun manualRegister(@PathVariable id: UUID, @Valid @RequestBody req: ManualRegisterRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): TournamentRegistration =
        service.manualRegister(id, req, a.adminId)

    @PostMapping("/registrations/{regId}/status")
    fun regStatus(@PathVariable regId: UUID, @Valid @RequestBody req: RegStatusRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): TournamentRegistration =
        service.setRegStatus(regId, req.status, a.adminId)
}
