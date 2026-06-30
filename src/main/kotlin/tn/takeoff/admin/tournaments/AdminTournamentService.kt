package tn.takeoff.admin.tournaments

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.admin.users.AdminUserService
import tn.takeoff.admin.users.CreateGhostRequest
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.tournaments.*
import java.time.Instant
import java.util.UUID

/** Composite detail for the admin tournament editor. */
data class TournamentDetail(
    val tournament: Tournament,
    val fields: List<TournamentField>,
    val pricing: List<TournamentPricing>,
    val promoCodes: List<TournamentPromoCode>,
    val confirmedCount: Long,
    val waitlistCount: Long,
)

@Service
class AdminTournamentService(
    private val tournaments: TournamentRepository,
    private val fields: TournamentFieldRepository,
    private val registrations: TournamentRegistrationRepository,
    private val pricing: TournamentPricingRepository,
    private val promoCodes: TournamentPromoCodeRepository,
    private val adminUserService: AdminUserService,
    private val auditService: AuditService,
) {

    fun list(): List<Tournament> = tournaments.findAllByOrderByStartsAtDesc()

    fun detail(id: UUID): TournamentDetail {
        val t = tournaments.findById(id).orElseThrow { NotFoundException("tournament", id) }
        return TournamentDetail(
            tournament = t,
            fields = fields.findByTournamentIdOrderByDisplayOrder(id),
            pricing = pricing.findByTournamentIdOrderByDisplayOrder(id),
            promoCodes = promoCodes.findByTournamentId(id),
            confirmedCount = registrations.countByTournamentIdAndStatus(id, RegStatus.CONFIRMED),
            waitlistCount = registrations.countByTournamentIdAndStatus(id, RegStatus.WAITLIST),
        )
    }

    @Transactional
    fun create(req: TournamentRequest, adminId: UUID): Tournament {
        val t = Tournament(
            title = req.title, description = req.description, bannerUrl = req.bannerUrl,
            format = req.format, category = req.category, startsAt = req.startsAt, endsAt = req.endsAt,
            entryFeeDt = req.entryFeeDt, prize = req.prize, maxParticipants = req.maxParticipants,
            registrationDeadline = req.registrationDeadline, registrationMode = req.registrationMode,
            autoWaitlist = req.autoWaitlist, manualValidation = req.manualValidation,
            paymentRule = req.paymentRule, createdByAdminId = adminId,
        )
        tournaments.save(t)
        auditService.log(adminId, "tournament.create", "tournament", t.id.toString())
        return t
    }

    @Transactional
    fun update(id: UUID, req: TournamentRequest, adminId: UUID): Tournament {
        val t = tournaments.findById(id).orElseThrow { NotFoundException("tournament", id) }
        t.title = req.title; t.description = req.description; t.bannerUrl = req.bannerUrl
        t.format = req.format; t.category = req.category; t.startsAt = req.startsAt; t.endsAt = req.endsAt
        t.entryFeeDt = req.entryFeeDt; t.prize = req.prize; t.maxParticipants = req.maxParticipants
        t.registrationDeadline = req.registrationDeadline; t.registrationMode = req.registrationMode
        t.autoWaitlist = req.autoWaitlist; t.manualValidation = req.manualValidation
        t.paymentRule = req.paymentRule; t.updatedAt = Instant.now()
        tournaments.save(t)
        auditService.log(adminId, "tournament.update", "tournament", id.toString())
        return t
    }

    @Transactional
    fun setStatus(id: UUID, status: TournamentStatus, adminId: UUID): Tournament {
        val t = tournaments.findById(id).orElseThrow { NotFoundException("tournament", id) }
        t.status = status; t.updatedAt = Instant.now()
        tournaments.save(t)
        auditService.log(adminId, "tournament.status", "tournament", id.toString(), mapOf("status" to status.name))
        return t
    }

    @Transactional
    fun delete(id: UUID, adminId: UUID) {
        val t = tournaments.findById(id).orElseThrow { NotFoundException("tournament", id) }
        tournaments.delete(t) // cascades to fields/pricing/promo/registrations via FK ON DELETE CASCADE
        auditService.log(adminId, "tournament.delete", "tournament", id.toString())
    }

    /** D-04: duplicate as a template (copies fields + pricing, resets to DRAFT). */
    @Transactional
    fun duplicate(id: UUID, adminId: UUID): Tournament {
        val src = tournaments.findById(id).orElseThrow { NotFoundException("tournament", id) }
        val copy = Tournament(
            title = src.title + " (copy)", description = src.description, bannerUrl = src.bannerUrl,
            format = src.format, category = src.category, startsAt = src.startsAt, endsAt = src.endsAt,
            entryFeeDt = src.entryFeeDt, prize = src.prize, maxParticipants = src.maxParticipants,
            registrationMode = src.registrationMode, autoWaitlist = src.autoWaitlist,
            manualValidation = src.manualValidation, paymentRule = src.paymentRule,
            status = TournamentStatus.DRAFT, createdByAdminId = adminId,
        )
        tournaments.save(copy)
        fields.findByTournamentIdOrderByDisplayOrder(id).forEach {
            fields.save(TournamentField(
                tournamentId = copy.id, fieldType = it.fieldType, label = it.label,
                helpText = it.helpText, required = it.required, options = it.options, displayOrder = it.displayOrder,
            ))
        }
        pricing.findByTournamentIdOrderByDisplayOrder(id).forEach {
            pricing.save(TournamentPricing(tournamentId = copy.id, label = it.label, priceDt = it.priceDt, displayOrder = it.displayOrder))
        }
        auditService.log(adminId, "tournament.duplicate", "tournament", copy.id.toString(), mapOf("from" to id.toString()))
        return copy
    }

    // ── form builder (D2) ──
    @Transactional
    fun addField(tournamentId: UUID, req: FieldRequest, adminId: UUID): TournamentField {
        tournaments.findById(tournamentId).orElseThrow { NotFoundException("tournament", tournamentId) }
        val f = TournamentField(
            tournamentId = tournamentId, fieldType = req.fieldType, label = req.label,
            helpText = req.helpText, required = req.required, options = req.options, displayOrder = req.displayOrder,
        )
        fields.save(f)
        auditService.log(adminId, "tournament.field_add", "tournament", tournamentId.toString())
        return f
    }

    @Transactional
    fun updateField(fieldId: UUID, req: FieldRequest, adminId: UUID): TournamentField {
        val f = fields.findById(fieldId).orElseThrow { NotFoundException("tournament_field", fieldId) }
        f.fieldType = req.fieldType; f.label = req.label; f.helpText = req.helpText
        f.required = req.required; f.options = req.options; f.displayOrder = req.displayOrder
        fields.save(f)
        auditService.log(adminId, "tournament.field_update", "tournament_field", fieldId.toString())
        return f
    }

    @Transactional
    fun deleteField(fieldId: UUID, adminId: UUID) {
        val f = fields.findById(fieldId).orElseThrow { NotFoundException("tournament_field", fieldId) }
        fields.delete(f)
        auditService.log(adminId, "tournament.field_delete", "tournament_field", fieldId.toString())
    }

    // ── pricing (replace whole set) ──
    @Transactional
    fun setPricing(tournamentId: UUID, list: List<PricingRequest>, adminId: UUID): List<TournamentPricing> {
        tournaments.findById(tournamentId).orElseThrow { NotFoundException("tournament", tournamentId) }
        pricing.deleteByTournamentId(tournamentId)
        val saved = list.mapIndexed { i, p ->
            pricing.save(TournamentPricing(tournamentId = tournamentId, label = p.label, priceDt = p.priceDt, displayOrder = if (p.displayOrder != 0) p.displayOrder else i))
        }
        auditService.log(adminId, "tournament.pricing", "tournament", tournamentId.toString())
        return saved
    }

    // ── promo codes ──
    @Transactional
    fun addPromo(tournamentId: UUID, req: PromoCodeRequest, adminId: UUID): TournamentPromoCode {
        tournaments.findById(tournamentId).orElseThrow { NotFoundException("tournament", tournamentId) }
        val p = TournamentPromoCode(
            tournamentId = tournamentId, code = req.code.trim().uppercase(), discountType = req.discountType,
            discountValue = req.discountValue, maxUses = req.maxUses, expiresAt = req.expiresAt,
        )
        promoCodes.save(p)
        auditService.log(adminId, "tournament.promo_add", "tournament", tournamentId.toString())
        return p
    }

    @Transactional
    fun deletePromo(promoId: UUID, adminId: UUID) {
        val p = promoCodes.findById(promoId).orElseThrow { NotFoundException("promo_code", promoId) }
        promoCodes.delete(p)
        auditService.log(adminId, "tournament.promo_delete", "tournament_promo_code", promoId.toString())
    }

    // ── registrations (D3) ──
    fun listRegistrations(tournamentId: UUID): List<TournamentRegistration> =
        registrations.findByTournamentIdOrderByCreatedAtDesc(tournamentId)

    @Transactional
    fun manualRegister(tournamentId: UUID, req: ManualRegisterRequest, adminId: UUID): TournamentRegistration {
        tournaments.findById(tournamentId).orElseThrow { NotFoundException("tournament", tournamentId) }
        val userId = when {
            req.userId != null -> req.userId
            !req.ghostName.isNullOrBlank() && !req.ghostPhone.isNullOrBlank() ->
                adminUserService.createGhost(CreateGhostRequest(req.ghostName, req.ghostPhone), adminId).id
            else -> throw BadRequestException("takeoff.reg.no_user", "Provide userId or ghostName + ghostPhone")
        }
        val reg = TournamentRegistration(
            tournamentId = tournamentId, userId = userId, categoryLabel = req.categoryLabel,
            answers = req.answers, status = RegStatus.CONFIRMED, createdByAdminId = adminId,
        )
        registrations.save(reg)
        auditService.log(adminId, "tournament.manual_register", "tournament_registration", reg.id.toString())
        return reg
    }

    @Transactional
    fun setRegStatus(regId: UUID, status: RegStatus, adminId: UUID): TournamentRegistration {
        val r = registrations.findById(regId).orElseThrow { NotFoundException("tournament_registration", regId) }
        r.status = status; r.updatedAt = Instant.now()
        if (status == RegStatus.CANCELLED || status == RegStatus.REJECTED) {
            r.paymentStatus = if (r.paymentStatus == RegPaymentStatus.PAID) RegPaymentStatus.REFUNDED else r.paymentStatus
        }
        registrations.save(r)
        auditService.log(adminId, "tournament.reg_status", "tournament_registration", regId.toString(), mapOf("status" to status.name))
        return r
    }
}
