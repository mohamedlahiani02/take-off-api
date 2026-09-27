package tn.takeoff.tournaments

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

enum class TournamentPaymentMethod { WALLET, AT_CLUB }

data class TournamentFieldDto(
    val id: UUID,
    val fieldType: FieldType,
    val label: String,
    val helpText: String?,
    val required: Boolean,
    val options: Map<String, Any>?,
    val displayOrder: Int,
)

data class TournamentPricingDto(val id: UUID, val label: String, val priceDt: BigDecimal, val displayOrder: Int)

data class MemberRegisterRequest(
    val pricingId: UUID? = null,
    val categoryLabel: String? = null,
    val answers: Map<String, Any> = emptyMap(),
    @field:NotNull val paymentMethod: TournamentPaymentMethod,
    val promoCode: String? = null,
    // Echoed from what the recap screen showed; the server never takes this as
    // authority, only checks it against the computed price (see below).
    val quotedPriceDt: BigDecimal? = null,
)

data class MemberRegistrationDto(
    val id: UUID,
    val tournamentId: UUID,
    val tournamentTitle: String? = null,
    val startsAt: Instant? = null,
    val categoryLabel: String?,
    val answers: Map<String, Any>,
    val paymentStatus: RegPaymentStatus,
    val status: RegStatus,
    val amountPaidDt: BigDecimal,
    val createdAt: Instant,
)

/**
 * The member side of tournament registration: detail -> form -> recap ->
 * payment/request -> status.
 *
 * Every rule here is enforced server-side because the only other write path
 * (AdminTournamentService.manualRegister) is deliberately permissive -- an
 * admin overriding capacity or a deadline by hand is a feature, not a bug it
 * shares with self-service. This controller is where "self-service" earns
 * its limits.
 */
@RestController
@RequestMapping("/api/v1/tournaments")
class MemberTournamentRegistrationController(
    private val tournaments: TournamentRepository,
    private val fields: TournamentFieldRepository,
    private val pricing: TournamentPricingRepository,
    private val promoCodes: TournamentPromoCodeRepository,
    private val registrations: TournamentRegistrationRepository,
    private val walletService: WalletService,
) {
    /** Public: what the registration form should render. Drafts stay unpublished. */
    @GetMapping("/{id}/fields")
    fun publicFields(@PathVariable id: UUID): List<TournamentFieldDto> {
        requirePublished(id)
        return fields.findByTournamentIdOrderByDisplayOrder(id).map {
            TournamentFieldDto(it.id, it.fieldType, it.label, it.helpText, it.required, it.options, it.displayOrder)
        }
    }

    /** Public: the price options a member can choose on the recap screen. */
    @GetMapping("/{id}/pricing")
    fun publicPricing(@PathVariable id: UUID): List<TournamentPricingDto> {
        requirePublished(id)
        return pricing.findByTournamentIdOrderByDisplayOrder(id).map {
            TournamentPricingDto(it.id, it.label, it.priceDt, it.displayOrder)
        }
    }

    private fun requirePublished(id: UUID) {
        val t = tournaments.findById(id).orElseThrow { NotFoundException("tournament", id) }
        if (t.status == TournamentStatus.DRAFT) throw NotFoundException("tournament", id)
    }

    /** The signed-in member's own registrations, for their profile. */
    @GetMapping("/registrations/mine")
    fun mine(@AuthenticationPrincipal claims: JwtService.Claims): List<MemberRegistrationDto> {
        val regs = registrations.findByUserIdOrderByCreatedAtDesc(claims.userId)
        if (regs.isEmpty()) return emptyList()
        val byId = tournaments.findAllById(regs.map { it.tournamentId }).associateBy { it.id }
        return regs.map { toDto(it, byId[it.tournamentId]?.title, byId[it.tournamentId]?.startsAt) }
    }

    @Transactional
    @PostMapping("/{id}/register")
    fun register(
        @PathVariable id: UUID,
        @Valid @RequestBody req: MemberRegisterRequest,
        @AuthenticationPrincipal claims: JwtService.Claims,
    ): MemberRegistrationDto {
        // Pessimistic lock: serialises two members racing for the tournament's
        // last confirmed spot, the same way court booking locks the court row.
        val t = tournaments.findByIdForUpdate(id).orElseThrow { NotFoundException("tournament", id) }
        // A draft is not discoverable at all (matches the public list/detail
        // endpoints), so registering against one 404s rather than leaking its
        // existence through a "registration closed" refusal.
        if (t.status == TournamentStatus.DRAFT) throw NotFoundException("tournament", id)

        if (t.status != TournamentStatus.REGISTRATION_OPEN) {
            throw BadRequestException("takeoff.tournament.registration_closed", "Registration is not open for this tournament")
        }
        if (t.registrationDeadline != null && Instant.now().isAfter(t.registrationDeadline)) {
            throw BadRequestException("takeoff.tournament.deadline_passed", "The registration deadline has passed")
        }
        if (t.registrationMode == RegistrationMode.INVITATION_ONLY) {
            throw BadRequestException("takeoff.tournament.invitation_only", "This tournament is invitation-only, contact the club")
        }
        val existing = registrations.findByTournamentIdAndUserIdAndStatusIn(
            id, claims.userId, listOf(RegStatus.PENDING, RegStatus.CONFIRMED, RegStatus.WAITLIST),
        )
        if (existing.isNotEmpty()) {
            throw BadRequestException("takeoff.tournament.already_registered", "You are already registered for this tournament")
        }

        // Required fields (including the CGU/agreement field, answered
        // truthfully) must be present before anything is charged.
        val formFields = fields.findByTournamentIdOrderByDisplayOrder(id)
        for (f in formFields.filter { it.required }) {
            val answer = req.answers[f.id.toString()]
            val present = when (f.fieldType) {
                FieldType.AGREEMENT -> answer == true || answer == "true"
                else -> answer != null && answer.toString().isNotBlank()
            }
            if (!present) {
                throw BadRequestException("takeoff.tournament.field_required", "'" + f.label + "' is required")
            }
        }

        // Price: an explicit tier if the tournament has any, else the flat entry fee.
        var price = if (req.pricingId != null) {
            pricing.findById(req.pricingId)
                .filter { it.tournamentId == id }
                .orElseThrow { BadRequestException("takeoff.tournament.invalid_pricing", "Unknown pricing option") }
                .priceDt
        } else {
            t.entryFeeDt
        }

        var promo: TournamentPromoCode? = null
        if (!req.promoCode.isNullOrBlank()) {
            promo = promoCodes.findByTournamentId(id).firstOrNull { it.code.equals(req.promoCode, ignoreCase = true) }
                ?: throw BadRequestException("takeoff.tournament.invalid_promo", "Unknown promo code")
            val maxUses = promo.maxUses
            if (promo.expiresAt != null && Instant.now().isAfter(promo.expiresAt)) {
                throw BadRequestException("takeoff.tournament.invalid_promo", "This promo code has expired")
            }
            if (maxUses != null && promo.uses >= maxUses) {
                throw BadRequestException("takeoff.tournament.invalid_promo", "This promo code has been fully redeemed")
            }
            price = when (promo.discountType) {
                DiscountType.FLAT -> (price - promo.discountValue).coerceAtLeast(BigDecimal.ZERO)
                DiscountType.PERCENT -> price.multiply(
                    BigDecimal.ONE - promo.discountValue.divide(BigDecimal(100), 6, RoundingMode.HALF_UP),
                ).setScale(3, RoundingMode.HALF_UP)
            }
        }

        // The recap screen's figure is checked, never trusted: configuration and
        // the promo code are the only price authority.
        if (req.quotedPriceDt != null && req.quotedPriceDt.compareTo(price) != 0) {
            throw BadRequestException("takeoff.tournament.price_mismatch", "The price has changed, reload and confirm again")
        }

        val allowed = when (t.paymentRule) {
            PaymentRule.ONLINE -> req.paymentMethod == TournamentPaymentMethod.WALLET
            PaymentRule.AT_CLUB -> req.paymentMethod == TournamentPaymentMethod.AT_CLUB
            PaymentRule.BOTH -> true
        }
        if (!allowed) {
            throw BadRequestException("takeoff.tournament.payment_method_not_allowed", "This tournament does not accept that payment method")
        }

        // Capacity, under the lock taken above: confirmed spots only, so a
        // waitlisted member never occupies one.
        val confirmedCount = registrations.countByTournamentIdAndStatus(id, RegStatus.CONFIRMED)
        val maxParticipants = t.maxParticipants
        val full = maxParticipants != null && confirmedCount >= maxParticipants

        val reg = TournamentRegistration(
            tournamentId = id, userId = claims.userId, categoryLabel = req.categoryLabel, answers = req.answers,
        )

        if (full) {
            if (!t.autoWaitlist) {
                throw BadRequestException("takeoff.tournament.full", "This tournament is full")
            }
            // A waitlisted spot is not a spot: nothing is collected for it, so a
            // member is never charged for a place that may never open up.
            if (req.paymentMethod == TournamentPaymentMethod.WALLET && price > BigDecimal.ZERO) {
                throw BadRequestException(
                    "takeoff.tournament.wallet_on_waitlist_not_allowed",
                    "The tournament is full, join the waitlist and pay once a spot opens",
                )
            }
            reg.status = RegStatus.WAITLIST
            reg.paymentStatus = RegPaymentStatus.PENDING
        } else {
            when (req.paymentMethod) {
                TournamentPaymentMethod.WALLET -> {
                    if (price > BigDecimal.ZERO) {
                        walletService.applyOnce(
                            userId = claims.userId, delta = price.negate(), type = WalletEntryType.PAYMENT,
                            reason = "tournament_registration", refType = "tournament_registration", refId = reg.id.toString(),
                        )
                    }
                    reg.paymentStatus = RegPaymentStatus.PAID
                    reg.amountPaidDt = price
                }
                TournamentPaymentMethod.AT_CLUB -> {
                    reg.paymentStatus = RegPaymentStatus.PAY_AT_CLUB
                }
            }
            // Both WALLET and AT_CLUB are final outcomes the instant this method
            // returns (no async gateway sits in between), so "confirmed" is
            // honest immediately. manualValidation still gates it: an admin who
            // wants to look at every entry before it counts is respected even
            // when payment already went through.
            reg.status = if (t.manualValidation) RegStatus.PENDING else RegStatus.CONFIRMED
        }

        registrations.save(reg)
        if (promo != null) {
            promo.uses += 1
            promoCodes.save(promo)
        }
        return toDto(reg, t.title, t.startsAt)
    }

    private fun toDto(r: TournamentRegistration, title: String? = null, startsAt: Instant? = null) = MemberRegistrationDto(
        id = r.id, tournamentId = r.tournamentId, tournamentTitle = title, startsAt = startsAt,
        categoryLabel = r.categoryLabel, answers = r.answers, paymentStatus = r.paymentStatus,
        status = r.status, amountPaidDt = r.amountPaidDt, createdAt = r.createdAt,
    )
}
