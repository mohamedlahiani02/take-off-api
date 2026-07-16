package tn.takeoff.admin.courts

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.admin.users.AdminUserService
import tn.takeoff.admin.users.CreateGhostRequest
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.ConflictException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.courts.BookingMode
import tn.takeoff.courts.BookingStatus
import tn.takeoff.courts.CourtActivity
import tn.takeoff.courts.CourtBlock
import tn.takeoff.courts.CourtBlockRepository
import tn.takeoff.courts.CourtBooking
import tn.takeoff.courts.CourtBookingRepository
import tn.takeoff.courts.CourtPaymentStatus
import tn.takeoff.courts.CourtRepository
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import java.time.Instant
import java.util.UUID

@Service
class AdminCourtService(
    private val courts: CourtRepository,
    private val bookings: CourtBookingRepository,
    private val blocks: CourtBlockRepository,
    private val adminUserService: AdminUserService,
    private val walletService: WalletService,
    private val auditService: AuditService,
) {

    /** C-01: calendar window [from, to). */
    fun calendar(from: Instant, to: Instant): CalendarDto = CalendarDto(
        courts = courts.findByActiveOrderByDisplayOrder(true).map(CourtDto::from),
        bookings = bookings.findByStartsAtGreaterThanEqualAndStartsAtLessThan(from, to).map(BookingDto::from),
        blocks = blocks.findByStartsAtGreaterThanEqualAndStartsAtLessThan(from, to).map(BlockDto::from),
    )

    /** C-02/03/04: book on behalf of an existing or freshly-created ghost user. */
    @Transactional
    fun createBooking(req: CreateBookingRequest, adminId: UUID): BookingDto {
        val court = courts.findById(req.courtId).orElseThrow { NotFoundException("court", req.courtId) }
        val endsAt = if (court.activity == CourtActivity.PADEL)
            req.startsAt.plusSeconds(5400) // padel is always fixed 90 minutes
        else req.endsAt
        if (endsAt <= req.startsAt) {
            throw BadRequestException("takeoff.booking.bad_time", "End must be after start")
        }

        val userId = resolveUser(req, adminId)
        assertSlotFree(req.courtId, req.startsAt, endsAt, req.mode, excludeBookingId = null)

        val booking = CourtBooking(
            courtId = req.courtId,
            userId = userId,
            startsAt = req.startsAt,
            endsAt = endsAt,
            mode = req.mode,
            priceDt = req.priceDt,
            paymentStatus = req.paymentStatus,
            paymentMethod = req.paymentMethod,
            createdByAdminId = adminId,
        )
        bookings.save(booking)
        auditService.log(adminId, "court.book", "court_booking", booking.id.toString())
        return BookingDto.from(booking)
    }

    /** C-05: cancel with optional wallet refund. */
    @Transactional
    fun cancel(id: UUID, req: CancelBookingRequest, adminId: UUID): BookingDto {
        val b = bookings.findById(id).orElseThrow { NotFoundException("court_booking", id) }
        if (b.status == BookingStatus.CANCELLED) {
            throw ConflictException("takeoff.booking.already_cancelled", "Booking already cancelled")
        }
        b.status = BookingStatus.CANCELLED
        b.cancelReason = req.reason
        b.cancelledAt = Instant.now()
        b.updatedAt = Instant.now()

        if (req.refundToWallet && b.userId != null && b.paymentStatus == CourtPaymentStatus.PAID) {
            walletService.apply(
                userId = b.userId!!,
                delta = b.priceDt,
                type = WalletEntryType.REFUND,
                reason = "Court booking cancelled: ${req.reason}",
                adminId = adminId,
                refType = "court_booking",
                refId = b.id.toString(),
            )
            b.paymentStatus = CourtPaymentStatus.REFUNDED
        }
        bookings.save(b)
        auditService.log(
            adminId, "court.cancel", "court_booking", id.toString(),
            mapOf("reason" to req.reason, "refunded" to req.refundToWallet),
        )
        return BookingDto.from(b)
    }

    /** C-06: reschedule to a new slot. */
    @Transactional
    fun reschedule(id: UUID, req: RescheduleRequest, adminId: UUID): BookingDto {
        if (req.endsAt <= req.startsAt) {
            throw BadRequestException("takeoff.booking.bad_time", "End must be after start")
        }
        val b = bookings.findById(id).orElseThrow { NotFoundException("court_booking", id) }
        if (b.status != BookingStatus.CONFIRMED) {
            throw ConflictException("takeoff.booking.not_active", "Only confirmed bookings can be rescheduled")
        }
        courts.findById(req.courtId).orElseThrow { NotFoundException("court", req.courtId) }
        assertSlotFree(req.courtId, req.startsAt, req.endsAt, b.mode, excludeBookingId = b.id)

        b.courtId = req.courtId
        b.startsAt = req.startsAt
        b.endsAt = req.endsAt
        b.updatedAt = Instant.now()
        bookings.save(b)
        auditService.log(adminId, "court.reschedule", "court_booking", id.toString())
        return BookingDto.from(b)
    }

    /** C-07/08: create a (optionally recurring) block. */
    @Transactional
    fun createBlock(req: CreateBlockRequest, adminId: UUID): BlockDto {
        if (req.endsAt <= req.startsAt) {
            throw BadRequestException("takeoff.block.bad_time", "End must be after start")
        }
        courts.findById(req.courtId).orElseThrow { NotFoundException("court", req.courtId) }
        val block = CourtBlock(
            courtId = req.courtId,
            startsAt = req.startsAt,
            endsAt = req.endsAt,
            reason = req.reason,
            recurringDow = req.recurringDow,
            recurringUntil = req.recurringUntil,
            createdByAdminId = adminId,
        )
        blocks.save(block)
        auditService.log(adminId, "court.block", "court_block", block.id.toString())
        return BlockDto.from(block)
    }

    @Transactional
    fun deleteBlock(id: UUID, adminId: UUID) {
        val block = blocks.findById(id).orElseThrow { NotFoundException("court_block", id) }
        blocks.delete(block)
        auditService.log(adminId, "court.unblock", "court_block", id.toString())
    }

    fun courtHistoryForUser(userId: UUID): List<CourtBookingHistoryDto> {
        val courtNames = courts.findAll().associate { it.id to it.name }
        return bookings.findByUserIdOrderByStartsAtDesc(userId).map { b ->
            CourtBookingHistoryDto(
                id = b.id,
                courtName = courtNames[b.courtId] ?: b.courtId.toString(),
                startsAt = b.startsAt,
                endsAt = b.endsAt,
                mode = b.mode.name,
                priceDt = b.priceDt,
                paymentStatus = b.paymentStatus.name,
                status = b.status.name,
                createdAt = b.createdAt,
            )
        }
    }

    // ── helpers ──

    private fun resolveUser(req: CreateBookingRequest, adminId: UUID): UUID = when {
        req.userId != null -> req.userId
        !req.ghostName.isNullOrBlank() && !req.ghostPhone.isNullOrBlank() ->
            adminUserService.createGhost(CreateGhostRequest(req.ghostName, req.ghostPhone), adminId).id
        else -> throw BadRequestException(
            "takeoff.booking.no_user",
            "Provide an existing userId or ghostName + ghostPhone",
        )
    }

    /**
     * Rejects if a one-off block overlaps, or if bookings conflict:
     * FULL needs an empty slot; SHARE only conflicts with an existing FULL booking.
     * (Recurring-block enforcement is computed in public availability — see follow-up.)
     */
    private fun assertSlotFree(courtId: UUID, startsAt: Instant, endsAt: Instant, mode: BookingMode, excludeBookingId: UUID?) {
        val blocking = blocks.findByCourtIdAndStartsAtLessThanAndEndsAtGreaterThan(courtId, endsAt, startsAt)
        if (blocking.isNotEmpty()) {
            throw ConflictException("takeoff.booking.slot_blocked", "This slot is blocked")
        }
        val overlaps = bookings
            .findByCourtIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(courtId, BookingStatus.CONFIRMED, endsAt, startsAt)
            .filter { it.id != excludeBookingId }
        when (mode) {
            BookingMode.FULL -> if (overlaps.isNotEmpty()) {
                throw ConflictException("takeoff.booking.slot_taken", "This slot is already booked")
            }
            BookingMode.SHARE -> if (overlaps.any { it.mode == BookingMode.FULL }) {
                throw ConflictException("takeoff.booking.slot_taken", "This slot is taken by a full-court booking")
            }
        }
    }
}
