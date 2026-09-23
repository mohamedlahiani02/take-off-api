package tn.takeoff.classes

import jakarta.persistence.EntityManager
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tn.takeoff.users.UserGateway
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import tn.takeoff.auth.JwtService
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.packs.CreditEntryType
import tn.takeoff.packs.PackActivity
import tn.takeoff.packs.PackCreditLedger
import tn.takeoff.packs.PackCreditLedgerRepository
import tn.takeoff.packs.PackType
import tn.takeoff.packs.PackTypeRepository
import tn.takeoff.packs.UserPack
import tn.takeoff.packs.UserPackRepository
import tn.takeoff.packs.UserPackStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// ── Public schedule ─────────────────────────────────────────────────────────

data class PublicSessionDto(
    val id: UUID,
    val classTypeId: UUID,
    val className: String,
    val level: String?,
    val instructorName: String?,
    val startsAt: Instant,
    val durationMin: Int,
    val priceDt: BigDecimal,
    val maxSpots: Int,
    val bookedSpots: Int,
    val waitlistCount: Int,
    val status: SessionStatus,
    val myBookingId: UUID?,
    val myStatus: ClassBookingStatus?,
)

@RestController
@RequestMapping("/api/v1/classes")
class MemberClassController(
    private val sessions: ClassSessionRepository,
    private val types: ClassTypeRepository,
    private val bookings: ClassBookingRepository,
    private val userPacks: UserPackRepository,
    private val ledger: PackCreditLedgerRepository,
    private val packTypes: PackTypeRepository,
    private val jwtService: JwtService,
    private val userGateway: UserGateway,
    private val walletService: WalletService,
    private val coaches: tn.takeoff.coaches.CoachRepository,
    private val em: EntityManager,
) {
    companion object {
        /**
         * Methods that settle a pack immediately. Anything else (pay-at-club, card) would hand out
         * credits before the money arrives and is refused until a settlement workflow exists.
         */
        private val SUPPORTED_PACK_PAYMENT_METHODS = setOf("WALLET")
        private const val MAX_PACK_QUANTITY = 10
    }

    // ── Public schedule ──────────────────────────────────────────────────────

    @GetMapping("/schedule")
    fun schedule(
        @RequestParam from: String,
        @RequestParam to: String,
        @RequestHeader(name = "Authorization", required = false) auth: String?,
    ): List<PublicSessionDto> {
        val fromInstant = Instant.parse(from)
        val toInstant = Instant.parse(to)
        val memberId = resolveMemberId(auth)

        val typeMap = types.findAll().associateBy { it.id }
        val coachMap = coaches.findAll().associateBy { it.id }
        val sessionList = sessions.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(fromInstant, toInstant)
            .filter { it.status == SessionStatus.SCHEDULED }

        val myBookings: Map<UUID, ClassBooking> = if (memberId != null)
            bookings.findByUserIdOrderByCreatedAtDesc(memberId).associateBy { it.sessionId }
        else emptyMap()

        return sessionList.map { s ->
            val all = bookings.findBySessionId(s.id)
            val booked = all.count { it.status == ClassBookingStatus.BOOKED || it.status == ClassBookingStatus.ATTENDED }
            val waitlist = all.count { it.status == ClassBookingStatus.WAITLIST }
            val mine = myBookings[s.id]
            PublicSessionDto(
                id = s.id, classTypeId = s.classTypeId,
                className = typeMap[s.classTypeId]?.name ?: "Class",
                level = typeMap[s.classTypeId]?.level,
                instructorName = s.instructorId?.let { coachMap[it] }?.let { "${it.firstName} ${it.lastName}" },
                startsAt = s.startsAt, durationMin = s.durationMin,
                priceDt = s.priceDt, maxSpots = s.maxSpots,
                bookedSpots = booked, waitlistCount = waitlist, status = s.status,
                myBookingId = mine?.id, myStatus = mine?.status,
            )
        }
    }

    // ── Member class booking ─────────────────────────────────────────────────

    data class BookRequest(@field:NotNull val sessionId: UUID)

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun book(
        @Valid @RequestBody req: BookRequest,
        @AuthenticationPrincipal claims: JwtService.Claims,
    ): Map<String, Any?> {
        val session = sessions.findByIdForUpdate(req.sessionId).orElseThrow { NotFoundException("session", req.sessionId) }
        if (session.status != SessionStatus.SCHEDULED)
            throw BadRequestException("takeoff.class.cancelled", "This session has been cancelled")
        if (session.startsAt.isBefore(Instant.now()))
            throw BadRequestException("takeoff.class.past_session", "Cannot book a session that has already started")

        val all = bookings.findBySessionId(session.id)
        val existing = all.firstOrNull {
            it.userId == claims.userId &&
            it.status !in listOf(ClassBookingStatus.CANCELLED, ClassBookingStatus.LATE_CANCEL)
        }
        if (existing != null) throw BadRequestException("takeoff.class.already_booked", "Already booked")

        val bookedCount = all.count { it.status == ClassBookingStatus.BOOKED || it.status == ClassBookingStatus.ATTENDED }
        val isFull = bookedCount >= session.maxSpots
        val waitlistPos = if (isFull) all.count { it.status == ClassBookingStatus.WAITLIST } + 1 else null

        val activePack = userPacks.findByUserIdOrderByPurchasedAtDesc(claims.userId)
            .firstOrNull {
                it.status == UserPackStatus.ACTIVE &&
                it.expiresAt.isAfter(Instant.now()) &&
                (it.unlimited || (it.creditsRemaining ?: 0) > 0) &&
                packTypes.findById(it.packTypeId).map { pt -> pt.activity == PackActivity.PILATES }.orElse(false)
            }
            ?.let { candidate ->
                val locked = userPacks.findByIdForUpdate(candidate.id).orElse(null) ?: return@let null
                em.refresh(locked)
                locked.takeIf {
                    it.status == UserPackStatus.ACTIVE &&
                    it.expiresAt.isAfter(Instant.now()) &&
                    (it.unlimited || (it.creditsRemaining ?: 0) > 0) &&
                    packTypes.findById(it.packTypeId).map { pt -> pt.activity == PackActivity.PILATES }.orElse(false)
                }
            }

        val paidWith: PaidWith
        val priceDt: BigDecimal
        val userPackId: UUID?

        if (activePack != null && !isFull) {
            paidWith = if (activePack.unlimited) PaidWith.UNLIMITED else PaidWith.PACK
            priceDt = BigDecimal.ZERO
            userPackId = activePack.id
            if (!activePack.unlimited) {
                val remaining = (activePack.creditsRemaining ?: 0) - 1
                activePack.creditsRemaining = remaining
                if (remaining <= 0) activePack.status = UserPackStatus.EXPIRED
                userPacks.save(activePack)
                ledger.save(PackCreditLedger(
                    userPackId = activePack.id, delta = -1,
                    type = CreditEntryType.CONSUME, reason = "class_booking",
                    refType = "class_session", refId = session.id.toString(),
                ))
            }
        } else {
            paidWith = PaidWith.SINGLE
            priceDt = session.priceDt
            userPackId = null
        }

        val booking = ClassBooking(
            sessionId = session.id, userId = claims.userId,
            status = if (isFull) ClassBookingStatus.WAITLIST else ClassBookingStatus.BOOKED,
            paidWith = paidWith, userPackId = userPackId, priceDt = priceDt,
            waitlistPosition = waitlistPos,
        )
        bookings.save(booking)

        return mapOf(
            "bookingId" to booking.id,
            "status" to booking.status,
            "paidWith" to paidWith,
            "priceDt" to priceDt,
            "waitlistPosition" to waitlistPos,
        )
    }

    @DeleteMapping("/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable id: UUID, @AuthenticationPrincipal claims: JwtService.Claims) {
        val booking = bookings.findById(id).orElseThrow { NotFoundException("booking", id) }
        if (booking.userId != claims.userId) throw BadRequestException("takeoff.forbidden", "Not your booking")
        if (booking.status == ClassBookingStatus.CANCELLED) throw BadRequestException("takeoff.class.already_cancelled", "Already cancelled")

        val session = sessions.findById(booking.sessionId).orElse(null)
        val hoursUntil = if (session != null) ChronoUnit.HOURS.between(Instant.now(), session.startsAt) else 0L
        val isLate = hoursUntil < 24

        booking.status = if (isLate) ClassBookingStatus.LATE_CANCEL else ClassBookingStatus.CANCELLED
        booking.updatedAt = Instant.now()

        if (!isLate && booking.paidWith == PaidWith.PACK && booking.userPackId != null) {
            userPacks.findById(booking.userPackId!!).ifPresent { up ->
                val restored = (up.creditsRemaining ?: 0) + 1
                up.creditsRemaining = restored
                if (up.status == UserPackStatus.EXPIRED) up.status = UserPackStatus.ACTIVE
                userPacks.save(up)
                ledger.save(PackCreditLedger(
                    userPackId = up.id, delta = 1,
                    type = CreditEntryType.REFUND, reason = "cancel_refund",
                    refType = "class_booking", refId = booking.id.toString(),
                ))
            }
        }

        bookings.save(booking)
    }

    @GetMapping("/bookings/mine")
    fun myBookings(@AuthenticationPrincipal claims: JwtService.Claims): List<Map<String, Any?>> {
        val typeMap = types.findAll().associateBy { it.id }
        return bookings.findByUserIdOrderByCreatedAtDesc(claims.userId).map { b ->
            val s = sessions.findById(b.sessionId).orElse(null)
            mapOf(
                "bookingId" to b.id, "status" to b.status, "paidWith" to b.paidWith,
                "priceDt" to b.priceDt, "waitlistPosition" to b.waitlistPosition, "createdAt" to b.createdAt,
                "session" to if (s != null) mapOf(
                    "id" to s.id, "startsAt" to s.startsAt, "durationMin" to s.durationMin,
                    "className" to (typeMap[s.classTypeId]?.name ?: "Class"), "status" to s.status,
                ) else null,
            )
        }
    }

    // ── Pack catalogue + purchase ────────────────────────────────────────────

    @GetMapping("/packs")
    fun publicPackTypes(): List<PackType> =
        packTypes.findAllByOrderByActivityAscDisplayOrderAsc().filter { it.active }

    /**
     * A pack is only issued against a settled payment. [paymentMethod] must therefore be one the
     * server can settle synchronously — see [SUPPORTED_PACK_PAYMENT_METHODS].
     */
    data class PurchasePackRequest(
        val packTypeId: UUID,
        val paymentMethod: String = "WALLET",
        val quantity: Int = 1,
    )

    @PostMapping("/packs/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun purchasePack(
        @RequestBody req: PurchasePackRequest,
        @AuthenticationPrincipal claims: JwtService.Claims,
    ): Map<String, Any?> {
        val method = req.paymentMethod.trim().uppercase()
        if (method !in SUPPORTED_PACK_PAYMENT_METHODS) {
            // Pay-at-club would mean issuing credits the club has not been paid for; there is no
            // reception-side settlement workflow yet, so it is refused rather than silently
            // charged to the wallet (which is what used to happen).
            throw BadRequestException(
                "takeoff.pack.unsupported_payment_method",
                "Packs can currently only be paid from the club wallet, not by '$method'. " +
                    "Top up your wallet or buy the pack at reception.",
            )
        }
        if (req.quantity < 1 || req.quantity > MAX_PACK_QUANTITY) {
            throw BadRequestException(
                "takeoff.pack.invalid_quantity",
                "Quantity must be between 1 and $MAX_PACK_QUANTITY",
            )
        }

        val packType = packTypes.findById(req.packTypeId).orElseThrow { NotFoundException("packType", req.packTypeId) }
        if (!packType.active) throw BadRequestException("takeoff.pack.inactive", "Pack not available")

        // Server-authoritative price: quantity x catalogue price, never a client-supplied total.
        val totalDt = packType.priceDt.multiply(BigDecimal(req.quantity))

        val user = userGateway.findById(claims.userId)
            .orElseThrow { NotFoundException("user", claims.userId) }
        if (user.walletDt < totalDt) {
            throw BadRequestException(
                "takeoff.wallet.insufficient_funds",
                "Insufficient wallet balance. Required: $totalDt DT, available: ${user.walletDt} DT."
            )
        }

        walletService.apply(
            userId = claims.userId,
            delta = totalDt.negate(),
            type = WalletEntryType.PAYMENT,
            reason = "pack_purchase",
            refType = "pack_type",
            refId = packType.id.toString(),
        )

        val expiresAt = Instant.now().plus((packType.validityMonths * 30).toLong(), ChronoUnit.DAYS)

        val issued = (1..req.quantity).map {
            val userPack = UserPack(
                userId = claims.userId,
                packTypeId = packType.id,
                creditsRemaining = packType.creditCount,
                unlimited = packType.unlimited,
                expiresAt = expiresAt,
            )
            userPacks.save(userPack)
            userPack
        }

        return mapOf(
            "userPackId" to issued.first().id,
            "userPackIds" to issued.map { it.id },
            "packName" to packType.name,
            "quantity" to req.quantity,
            "credits" to packType.creditCount, "unlimited" to packType.unlimited,
            "expiresAt" to expiresAt,
            "priceDt" to packType.priceDt, "totalDt" to totalDt,
            "paymentMethod" to method,
        )
    }

    @GetMapping("/packs/mine")
    fun myPacks(@AuthenticationPrincipal claims: JwtService.Claims): List<Map<String, Any?>> {
        val typeMap = packTypes.findAll().associateBy { it.id }
        return userPacks.findByUserIdOrderByPurchasedAtDesc(claims.userId).map { up ->
            mapOf(
                "id" to up.id, "status" to up.status,
                "creditsRemaining" to up.creditsRemaining, "unlimited" to up.unlimited,
                "expiresAt" to up.expiresAt, "purchasedAt" to up.purchasedAt,
                "packName" to (typeMap[up.packTypeId]?.name ?: "Pack"),
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun resolveMemberId(authHeader: String?): UUID? {
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) return null
        return try { jwtService.verify(authHeader.removePrefix("Bearer ").trim()).userId } catch (_: Exception) { null }
    }
}
