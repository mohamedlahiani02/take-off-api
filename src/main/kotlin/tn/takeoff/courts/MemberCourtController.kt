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
    private val walletService: WalletService,
    private val jwtService: JwtService,
) {
    companion object {
        private val TUNIS = ZoneId.of("Africa/Tunis")
        private val SLOT_DURATION = Duration.ofMinutes(90)
        private val OPEN = LocalTime.of(7, 0)
        private val CLOSE = LocalTime.of(22, 0)
        val DEFAULT_PRICE_DT: BigDecimal = BigDecimal("80.000")
    }

    // ── GET /api/v1/courts/{id}/slots?date=YYYY-MM-DD ────────────────────

    data class SlotDto(
        val startsAt: Instant,
        val endsAt: Instant,
        val available: Boolean,
        val reason: String?,   // "BOOKED" | "BLOCKED" | null
    )

    @GetMapping("/{id}/slots")
    fun slots(
        @PathVariable id: UUID,
        @RequestParam date: String,
        @RequestHeader(name = "Authorization", required = false) auth: String?,
    ): List<SlotDto> {
        courts.findById(id).orElseThrow { NotFoundException("court", id) }
        val localDate = LocalDate.parse(date)

        // Generate 90-min slots 07:00–22:00 in Tunis time
        val slotStarts = mutableListOf<ZonedDateTime>()
        var cursor = ZonedDateTime.of(localDate, OPEN, TUNIS)
        val close = ZonedDateTime.of(localDate, CLOSE, TUNIS)
        while (!cursor.plus(SLOT_DURATION).isAfter(close)) {
            slotStarts.add(cursor)
            cursor = cursor.plus(SLOT_DURATION)
        }

        val dayStart = ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, TUNIS).toInstant()
        val dayEnd = dayStart.plus(Duration.ofDays(1))

        val confirmedBookings = bookings.findByCourtIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            courtId = id, status = BookingStatus.CONFIRMED,
            endsAt = dayEnd, startsAt = dayStart,
        )

        val allCourtBlocks = blocks.findByCourtId(id)
        // Java DayOfWeek: MON=1..SUN=7 → convert to 0=Sun..6=Sat to match recurringDow
        val dow = localDate.dayOfWeek.value % 7

        return slotStarts.map { zdtStart ->
            val slotStart = zdtStart.toInstant()
            val slotEnd = zdtStart.plus(SLOT_DURATION).toInstant()

            val isBooked = confirmedBookings.any { b -> b.startsAt < slotEnd && b.endsAt > slotStart }
            if (isBooked) return@map SlotDto(slotStart, slotEnd, false, "BOOKED")

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
        val court = courts.findById(id).orElseThrow { NotFoundException("court", id) }
        val startsAt = Instant.parse(req.startsAt)
        val endsAt = startsAt.plus(SLOT_DURATION)

        val conflicts = bookings.findByCourtIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            courtId = id, status = BookingStatus.CONFIRMED,
            endsAt = endsAt, startsAt = startsAt,
        )
        if (conflicts.isNotEmpty())
            throw BadRequestException("takeoff.court.slot_taken", "This slot is already booked")

        val priceDt = DEFAULT_PRICE_DT
        val paymentStatus = when (req.paymentMethod) {
            CourtPaymentMethod.WALLET -> {
                walletService.apply(
                    userId = claims.userId, delta = priceDt.negate(),
                    type = WalletEntryType.PAYMENT, reason = "court_booking",
                    refType = "court_booking", refId = court.id.toString(),
                )
                CourtPaymentStatus.PAID
            }
            CourtPaymentMethod.PAY_AT_CLUB -> CourtPaymentStatus.PAY_AT_CLUB
            else -> CourtPaymentStatus.PENDING
        }

        val booking = CourtBooking(
            courtId = id, userId = claims.userId,
            startsAt = startsAt, endsAt = endsAt,
            mode = req.mode, priceDt = priceDt,
            paymentStatus = paymentStatus, paymentMethod = req.paymentMethod,
        )
        bookings.save(booking)

        return mapOf(
            "bookingId" to booking.id, "courtName" to court.name,
            "startsAt" to startsAt, "endsAt" to endsAt,
            "paymentStatus" to paymentStatus, "priceDt" to priceDt,
        )
    }

    // ── GET /api/v1/courts/bookings/mine ──────────────────────────────────

    @GetMapping("/bookings/mine")
    fun myBookings(@AuthenticationPrincipal claims: JwtService.Claims): List<Map<String, Any?>> {
        val courtMap = courts.findAll().associateBy { it.id }
        return bookings.findByUserIdOrderByStartsAtDesc(claims.userId).map { b ->
            mapOf(
                "bookingId" to b.id, "courtId" to b.courtId,
                "courtName" to (courtMap[b.courtId]?.name ?: "Court"),
                "startsAt" to b.startsAt, "endsAt" to b.endsAt,
                "status" to b.status, "paymentStatus" to b.paymentStatus,
                "priceDt" to b.priceDt, "mode" to b.mode, "createdAt" to b.createdAt,
            )
        }
    }

    // ── DELETE /api/v1/courts/bookings/{id} ───────────────────────────────

    @DeleteMapping("/bookings/{bookingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelBooking(
        @PathVariable bookingId: UUID,
        @AuthenticationPrincipal claims: JwtService.Claims,
    ) {
        val booking = bookings.findById(bookingId).orElseThrow { NotFoundException("court_booking", bookingId) }
        if (booking.userId != claims.userId)
            throw BadRequestException("takeoff.forbidden", "Not your booking")
        if (booking.status == BookingStatus.CANCELLED)
            throw BadRequestException("takeoff.court.already_cancelled", "Already cancelled")

        val hoursUntil = Duration.between(Instant.now(), booking.startsAt).toHours()
        if (hoursUntil < 24)
            throw BadRequestException("takeoff.court.cancel_too_late", "Cannot cancel less than 24 hours before the slot")

        if (booking.paymentMethod == CourtPaymentMethod.WALLET && booking.paymentStatus == CourtPaymentStatus.PAID) {
            walletService.apply(
                userId = claims.userId, delta = booking.priceDt,
                type = WalletEntryType.REFUND, reason = "court_booking_cancel",
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

