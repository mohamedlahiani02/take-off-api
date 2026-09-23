package tn.takeoff.courts

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.util.UUID

@RestController
@RequestMapping("/api/v1/courts")
class MemberCourtController(
    private val courts: CourtRepository,
    private val bookings: CourtBookingRepository,
    private val blocks: CourtBlockRepository,
    private val players: CourtBookingPlayerRepository,
    private val walletService: WalletService,
    private val jwtService: JwtService,
) {
    companion object {
        // Slot geometry lives in CourtSlots so availability and booking cannot drift apart.
        private val TUNIS = CourtSlots.TUNIS
        private val SLOT_DURATION = CourtSlots.SLOT_DURATION
        val DEFAULT_PRICE_DT: BigDecimal = BigDecimal("80.000")
    }

    // ── GET /api/v1/courts/{id}/slots?date=YYYY-MM-DD ────────────────────

    data class SlotDto(
        val startsAt: Instant,
        val endsAt: Instant,
        val available: Boolean,
        val reason: String?,   // "BOOKED" | "BLOCKED" | "SHARE_OPEN" | null
        val openShareSlots: Int = 0,   // free tranches when a shared match is joinable
    )

    @GetMapping("/{id}/slots")
    fun slots(
        @PathVariable id: UUID,
        @RequestParam date: String,
        @RequestHeader(name = "Authorization", required = false) auth: String?,
    ): List<SlotDto> {
        courts.findById(id).orElseThrow { NotFoundException("court", id) }
        val localDate = LocalDate.parse(date)

        // Same grid the booking path validates against (CourtSlots).
        val slotStarts = CourtSlots.startsFor(localDate)

        val dayStart = ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, TUNIS).toInstant()
        val dayEnd = dayStart.plus(Duration.ofDays(1))

        val confirmedBookings = bookings.findByCourtIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            courtId = id, status = BookingStatus.CONFIRMED,
            endsAt = dayEnd, startsAt = dayStart,
        )
        val playerCounts = if (confirmedBookings.isEmpty()) emptyMap()
        else players.findByBookingIdIn(confirmedBookings.map { it.id })
            .groupingBy { it.bookingId }.eachCount()

        val allCourtBlocks = blocks.findByCourtId(id)
        // Java DayOfWeek: MON=1..SUN=7 → convert to 0=Sun..6=Sat to match recurringDow
        val dow = localDate.dayOfWeek.value % 7

        return slotStarts.map { zdtStart ->
            val slotStart = zdtStart.toInstant()
            val slotEnd = zdtStart.plus(SLOT_DURATION).toInstant()

            val overlapping = confirmedBookings.filter { b -> b.startsAt < slotEnd && b.endsAt > slotStart }
            if (overlapping.isNotEmpty()) {
                // A shared match with free tranches is joinable, not "taken" (US-1.1).
                val openShare = overlapping
                    .filter { it.mode == BookingMode.SHARE }
                    .takeIf { it.size == overlapping.size } // no FULL booking in the slot
                    ?.firstOrNull { (playerCounts[it.id] ?: 1) < 4 }
                if (openShare != null) {
                    return@map SlotDto(slotStart, slotEnd, true, "SHARE_OPEN", 4 - (playerCounts[openShare.id] ?: 1))
                }
                return@map SlotDto(slotStart, slotEnd, false, "BOOKED")
            }

            val slotStartTime = zdtStart.toLocalTime()
            val slotEndTime = zdtStart.plus(SLOT_DURATION).toLocalTime()

            val isBlocked = allCourtBlocks.any { bl ->
                if (bl.recurringDow == null) {
                    bl.startsAt < slotEnd && bl.endsAt > slotStart
                } else {
                    bl.recurringDow == dow &&
                    (bl.recurringUntil == null || !localDate.isAfter(bl.recurringUntil)) &&
                    bl.startsAt.atZone(TUNIS).toLocalTime() < slotEndTime &&
                    bl.endsAt.atZone(TUNIS).toLocalTime() > slotStartTime
                }
            }
            if (isBlocked) return@map SlotDto(slotStart, slotEnd, false, "BLOCKED")

            SlotDto(slotStart, slotEnd, true, null)
        }
    }

    // ── POST /api/v1/courts/{id}/bookings ─────────────────────────────────

    data class BookCourtRequest(
        @field:NotNull val startsAt: String,
        val mode: BookingMode = BookingMode.FULL,
        val paymentMethod: CourtPaymentMethod = CourtPaymentMethod.PAY_AT_CLUB,
    )

    @PostMapping("/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun book(
        @PathVariable id: UUID,
        @Valid @RequestBody req: BookCourtRequest,
        @AuthenticationPrincipal claims: JwtService.Claims,
    ): Map<String, Any?> {
        val court = courts.findByIdForUpdate(id).orElseThrow { NotFoundException("court", id) }
        val startsAt = Instant.parse(req.startsAt)
        val endsAt = startsAt.plus(SLOT_DURATION)

        if (!court.active)
            throw BadRequestException("takeoff.court.unavailable", "Court is not available")
        if (!startsAt.isAfter(Instant.now()))
            throw BadRequestException("takeoff.court.past_slot", "Cannot book a slot in the past")

        // Rejects off-grid starts and any slot that would run past closing time or midnight.
        CourtSlots.requireBookableStart(startsAt)

        val localDate = startsAt.atZone(TUNIS).toLocalDate()
        val slotStartTime = startsAt.atZone(TUNIS).toLocalTime()
        val slotEndTime = endsAt.atZone(TUNIS).toLocalTime()
        val dow = localDate.dayOfWeek.value % 7
        val allCourtBlocks = blocks.findByCourtId(id)
        val isBlocked = allCourtBlocks.any { bl ->
            if (bl.recurringDow == null) {
                bl.startsAt < endsAt && bl.endsAt > startsAt
            } else {
                bl.recurringDow == dow &&
                (bl.recurringUntil == null || !localDate.isAfter(bl.recurringUntil)) &&
                bl.startsAt.atZone(TUNIS).toLocalTime() < slotEndTime &&
                bl.endsAt.atZone(TUNIS).toLocalTime() > slotStartTime
            }
        }
        if (isBlocked)
            throw BadRequestException("takeoff.court.slot_blocked", "This slot is not available for booking")

        val conflicts = bookings.findByCourtIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            courtId = id, status = BookingStatus.CONFIRMED,
            endsAt = endsAt, startsAt = startsAt,
        )

        // SHARE: joining an existing shared match takes an open tranche slot (US-1.1/2.5)
        // instead of creating an overlapping booking.
        if (req.mode == BookingMode.SHARE) {
            val openMatch = conflicts.firstOrNull { it.mode == BookingMode.SHARE }
            if (conflicts.any { it.mode == BookingMode.FULL })
                throw BadRequestException("takeoff.court.slot_taken", "This slot is already booked")
            if (openMatch != null) return joinSharedMatch(openMatch, court.name, req, claims.userId)
        } else if (conflicts.isNotEmpty()) {
            throw BadRequestException("takeoff.court.slot_taken", "This slot is already booked")
        }

        val priceDt = if (req.mode == BookingMode.SHARE)
            DEFAULT_PRICE_DT.divide(BigDecimal(4)) else DEFAULT_PRICE_DT
        val paymentStatus = when {
            req.paymentMethod == CourtPaymentMethod.WALLET -> {
                walletService.apply(
                    userId = claims.userId, delta = priceDt.negate(),
                    type = WalletEntryType.PAYMENT, reason = "court_booking",
                    refType = "court_booking", refId = court.id.toString(),
                )
                if (req.mode == BookingMode.SHARE) CourtPaymentStatus.PARTIAL else CourtPaymentStatus.PAID
            }
            req.paymentMethod == CourtPaymentMethod.PAY_AT_CLUB -> CourtPaymentStatus.PAY_AT_CLUB
            else -> CourtPaymentStatus.PENDING
        }

        val booking = CourtBooking(
            courtId = id, userId = claims.userId,
            startsAt = startsAt, endsAt = endsAt,
            mode = req.mode, priceDt = priceDt,
            paymentStatus = paymentStatus, paymentMethod = req.paymentMethod,
        )
        bookings.save(booking)

        // Organizer is always participant #1 of the match (per-player payment tracking).
        val organizerPaid = req.paymentMethod == CourtPaymentMethod.WALLET
        players.save(CourtBookingPlayer(
            bookingId = booking.id,
            userId = claims.userId,
            shareDt = priceDt,
            paymentStatus = if (organizerPaid) PlayerPaymentStatus.PAID else PlayerPaymentStatus.PENDING,
            paymentMethod = when (req.paymentMethod) {
                CourtPaymentMethod.D17 -> PlayerPaymentMethod.D17
                CourtPaymentMethod.WALLET -> PlayerPaymentMethod.WALLET
                CourtPaymentMethod.CARD -> PlayerPaymentMethod.CARD
                CourtPaymentMethod.CASH -> PlayerPaymentMethod.CASH
                CourtPaymentMethod.PAY_AT_CLUB -> null
            },
            paidAt = if (organizerPaid) Instant.now() else null,
        ))

        return mapOf(
            "bookingId" to booking.id, "courtName" to court.name,
            "startsAt" to startsAt, "endsAt" to endsAt,
            "paymentStatus" to paymentStatus, "priceDt" to priceDt,
        )
    }

    /** Join an existing shared match: one participant row, no overlapping booking. */
    private fun joinSharedMatch(match: CourtBooking, courtName: String, req: BookCourtRequest, userId: UUID): Map<String, Any?> {
        val existing = players.findByBookingId(match.id)
        if (existing.any { it.userId == userId })
            throw BadRequestException("takeoff.court.already_joined", "You are already in this match")
        if (existing.size >= 4)
            throw BadRequestException("takeoff.court.match_full", "This match is already full")

        val share = match.priceDt
        val paid = req.paymentMethod == CourtPaymentMethod.WALLET
        if (paid) {
            walletService.apply(
                userId = userId, delta = share.negate(),
                type = WalletEntryType.PAYMENT, reason = "court_booking_share",
                refType = "court_booking", refId = match.id.toString(),
            )
        }
        players.save(CourtBookingPlayer(
            bookingId = match.id, userId = userId, shareDt = share,
            paymentStatus = if (paid) PlayerPaymentStatus.PAID else PlayerPaymentStatus.PENDING,
            paymentMethod = if (paid) PlayerPaymentMethod.WALLET else null,
            paidAt = if (paid) Instant.now() else null,
        ))
        return mapOf(
            "bookingId" to match.id, "courtName" to courtName,
            "startsAt" to match.startsAt, "endsAt" to match.endsAt,
            "paymentStatus" to (if (paid) CourtPaymentStatus.PAID else CourtPaymentStatus.PAY_AT_CLUB),
            "priceDt" to share, "joined" to true,
        )
    }

    // ── GET /api/v1/courts/bookings/mine ──────────────────────────────────

    @GetMapping("/bookings/mine")
    fun myBookings(@AuthenticationPrincipal claims: JwtService.Claims): List<Map<String, Any?>> {
        val courtMap = courts.findAll().associateBy { it.id }
        val organized = bookings.findByUserIdOrderByStartsAtDesc(claims.userId)
        // Matches joined as a participant (not organizer) also belong in my history (US-4.2).
        val joinedIds = players.findByUserId(claims.userId).map { it.bookingId }.toSet() -
            organized.map { it.id }.toSet()
        val joined = if (joinedIds.isEmpty()) emptyList() else bookings.findAllById(joinedIds)
        val myShares = players.findByUserId(claims.userId).associateBy { it.bookingId }
        return (organized.map { it to true } + joined.map { it to false })
            .sortedByDescending { it.first.startsAt }
            .map { (b, isOrganizer) ->
                val share = myShares[b.id]
                mapOf(
                    "bookingId" to b.id, "courtId" to b.courtId,
                    "courtName" to (courtMap[b.courtId]?.name ?: "Court"),
                    "startsAt" to b.startsAt, "endsAt" to b.endsAt,
                    "status" to b.status, "paymentStatus" to b.paymentStatus,
                    "priceDt" to b.priceDt, "mode" to b.mode, "createdAt" to b.createdAt,
                    "isOrganizer" to isOrganizer,
                    "myShareDt" to share?.shareDt,
                    "mySharePaymentStatus" to share?.paymentStatus,
                )
            }
    }

    // ── DELETE /api/v1/courts/bookings/{id} ───────────────────────────────

    @DeleteMapping("/bookings/{bookingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun cancelBooking(
        @PathVariable bookingId: UUID,
        @AuthenticationPrincipal claims: JwtService.Claims,
    ) {
        val booking = bookings.findByIdForUpdate(bookingId).orElseThrow { NotFoundException("court_booking", bookingId) }
        if (booking.status == BookingStatus.CANCELLED)
            throw BadRequestException("takeoff.court.already_cancelled", "Already cancelled")

        val hoursUntil = Duration.between(Instant.now(), booking.startsAt).toHours()
        if (hoursUntil < 24)
            throw BadRequestException("takeoff.court.cancel_too_late", "Cannot cancel less than 24 hours before the slot")

        // Non-organizer participant leaving a shared match frees only their tranche (US-2.5).
        if (booking.userId != claims.userId) {
            val mine = players.findByBookingId(bookingId).firstOrNull { it.userId == claims.userId }
                ?: throw BadRequestException("takeoff.forbidden", "Not your booking")
            if (mine.paymentStatus == PlayerPaymentStatus.PAID && mine.paymentMethod == PlayerPaymentMethod.WALLET) {
                walletService.apply(
                    userId = claims.userId, delta = mine.shareDt,
                    type = WalletEntryType.REFUND, reason = "court_booking_share_cancel",
                    refType = "court_booking", refId = booking.id.toString(),
                )
            }
            players.delete(mine)
            return
        }

        val allPlayers = players.findByBookingId(bookingId)
        allPlayers
            .filter { it.paymentStatus == PlayerPaymentStatus.PAID && it.paymentMethod == PlayerPaymentMethod.WALLET }
            .forEach { p ->
                walletService.apply(
                    userId = p.userId, delta = p.shareDt,
                    type = WalletEntryType.REFUND,
                    reason = if (p.userId == booking.userId) "court_booking_cancel" else "court_booking_share_cancel",
                    refType = "court_booking", refId = booking.id.toString(),
                )
            }

        booking.status = BookingStatus.CANCELLED
        booking.cancelledAt = Instant.now()
        booking.updatedAt = Instant.now()
        bookings.save(booking)
    }

    private fun resolveMemberId(authHeader: String?): UUID? {
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) return null
        return try { jwtService.verify(authHeader.removePrefix("Bearer ").trim()).userId } catch (_: Exception) { null }
    }
}

