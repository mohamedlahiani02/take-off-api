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
import tn.takeoff.courts.CourtBookingPlayer
import tn.takeoff.courts.CourtBookingPlayerRepository
import tn.takeoff.courts.CourtPaymentMethod
import tn.takeoff.courts.CourtPaymentStatus
import tn.takeoff.courts.CourtRepository
import tn.takeoff.courts.CourtSlots
import tn.takeoff.courts.PlayerPaymentMethod
import tn.takeoff.courts.PlayerPaymentStatus
import tn.takeoff.users.UserRepository
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import java.time.Instant
import java.util.UUID

@Service
class AdminCourtService(
    private val courts: CourtRepository,
    private val bookings: CourtBookingRepository,
    private val blocks: CourtBlockRepository,
    private val players: CourtBookingPlayerRepository,
    private val adminUserService: AdminUserService,
    private val userRepo: UserRepository,
    private val walletService: WalletService,
    private val auditService: AuditService,
) {

    /** C-01: calendar window [from, to). Bookings carry participants + derived payment state. */
    fun calendar(from: Instant, to: Instant): CalendarDto {
        val calBookings = bookings.findByStartsAtGreaterThanEqualAndStartsAtLessThan(from, to)
        val playersByBooking = if (calBookings.isEmpty()) emptyMap()
        else players.findByBookingIdIn(calBookings.map { it.id }).groupBy { it.bookingId }
        val userIds = calBookings.mapNotNull { it.userId }.toSet() +
            playersByBooking.values.flatten().map { it.userId }
        val userNames = if (userIds.isEmpty()) emptyMap()
        else userRepo.findAllById(userIds).associate { it.id to it.name }
        return CalendarDto(
            courts = courts.findByActiveOrderByDisplayOrder(true).map(CourtDto::from),
            bookings = calBookings.map {
                BookingDto.from(it, userNames[it.userId], playersByBooking[it.id] ?: emptyList(), userNames)
            },
            blocks = blocks.findByStartsAtGreaterThanEqualAndStartsAtLessThan(from, to).map(BlockDto::from),
        )
    }

    /** P-00: one booking with its participant slots. */
    fun bookingDetail(id: UUID): BookingDto {
        val b = bookings.findById(id).orElseThrow { NotFoundException("court_booking", id) }
        return dtoWithPlayers(b)
    }

    /** C-02/03/04: book on behalf of an existing or freshly-created ghost user. */
    @Transactional
    fun createBooking(req: CreateBookingRequest, adminId: UUID): BookingDto {
        val court = courts.findByIdForUpdate(req.courtId).orElseThrow { NotFoundException("court", req.courtId) }
        val endsAt = if (court.activity == CourtActivity.PADEL)
            req.startsAt.plusSeconds(5400) // padel is always fixed 90 minutes
        else req.endsAt
        if (endsAt <= req.startsAt) {
            throw BadRequestException("takeoff.booking.bad_time", "End must be after start")
        }
        // Padel shares the member slot grid; other activities keep their explicit window.
        if (court.activity == CourtActivity.PADEL) CourtSlots.requireBookableStart(req.startsAt)

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

        // The organizer is always participant #1; their tranche mirrors the booking payment.
        val organizer = CourtBookingPlayer(
            bookingId = booking.id,
            userId = userId,
            shareDt = req.priceDt,
            paymentStatus = if (req.paymentStatus == CourtPaymentStatus.PAID)
                PlayerPaymentStatus.PAID else PlayerPaymentStatus.PENDING,
            paymentMethod = req.paymentMethod?.toPlayerMethod(),
            paidAt = if (req.paymentStatus == CourtPaymentStatus.PAID) Instant.now() else null,
            addedByAdminId = adminId,
        )
        players.save(organizer)

        auditService.log(adminId, "court.book", "court_booking", booking.id.toString())
        return dtoWithPlayers(booking)
    }

    // ── participants (Epic 1) ──

    /** P-01: attach a member (existing or created inline) to an open slot. */
    @Transactional
    fun addParticipant(bookingId: UUID, req: AddParticipantRequest, adminId: UUID): BookingDto {
        val b = bookings.findById(bookingId).orElseThrow { NotFoundException("court_booking", bookingId) }
        if (b.status == BookingStatus.CANCELLED) {
            throw ConflictException("takeoff.booking.cancelled", "Booking is cancelled")
        }
        val existing = players.findByBookingId(bookingId)
        val maxSlots = if (b.mode == BookingMode.SHARE) BookingDto.SHARE_SLOTS else 1
        if (existing.size >= maxSlots) {
            throw ConflictException("takeoff.booking.match_full", "All $maxSlots player slots are taken")
        }
        val userId = when {
            req.userId != null -> req.userId
            !req.newMemberName.isNullOrBlank() && !req.newMemberPhone.isNullOrBlank() ->
                adminUserService.createGhost(CreateGhostRequest(req.newMemberName, req.newMemberPhone), adminId).id
            else -> throw BadRequestException(
                "takeoff.participant.no_user",
                "Provide an existing userId or newMemberName + newMemberPhone",
            )
        }
        if (players.existsByBookingIdAndUserId(bookingId, userId)) {
            throw ConflictException("takeoff.participant.duplicate", "This member is already in the match")
        }
        val share = req.shareDt ?: b.priceDt
        players.save(CourtBookingPlayer(
            bookingId = bookingId, userId = userId, shareDt = share, addedByAdminId = adminId,
        ))
        auditService.log(adminId, "court.participant_add", "court_booking", bookingId.toString(),
            mapOf("userId" to userId.toString()))
        return dtoWithPlayers(b)
    }

    /** P-02: settle / adjust one tranche; WALLET method actually debits the wallet. */
    @Transactional
    fun updateParticipant(bookingId: UUID, participantId: UUID, req: UpdateParticipantRequest, adminId: UUID): BookingDto {
        val b = bookings.findById(bookingId).orElseThrow { NotFoundException("court_booking", bookingId) }
        val p = players.findById(participantId)
            .filter { it.bookingId == bookingId }
            .orElseThrow { NotFoundException("court_booking_player", participantId) }

        if (req.paymentStatus != null && req.paymentStatus != p.paymentStatus) {
            if (req.paymentStatus == PlayerPaymentStatus.PAID) {
                if (req.paymentMethod == PlayerPaymentMethod.WALLET) {
                    val payer = p.userId ?: throw BadRequestException(
                        "takeoff.court.guest_wallet",
                        "A guest has no wallet — record cash or card instead",
                    )
                    walletService.apply(
                        userId = payer, delta = p.shareDt.negate(), type = WalletEntryType.PAYMENT,
                        reason = "Court booking share", adminId = adminId,
                        refType = "court_booking_player", refId = p.id.toString(),
                    )
                }
                p.paymentMethod = req.paymentMethod
                p.paidAt = Instant.now()
            } else {
                p.paidAt = null
                p.paymentMethod = null
            }
            p.paymentStatus = req.paymentStatus
        }
        if (req.noShow != null) p.noShow = req.noShow
        p.updatedAt = Instant.now()
        players.save(p)

        syncBookingPaymentState(b)
        auditService.log(adminId, "court.participant_update", "court_booking_player", p.id.toString(),
            mapOf("status" to p.paymentStatus.name, "noShow" to p.noShow))
        return dtoWithPlayers(b)
    }

    /** P-03: detach a participant (their tranche slot reopens — US-2.5 admin side). */
    @Transactional
    fun removeParticipant(bookingId: UUID, participantId: UUID, adminId: UUID): BookingDto {
        val b = bookings.findById(bookingId).orElseThrow { NotFoundException("court_booking", bookingId) }
        val p = players.findById(participantId)
            .filter { it.bookingId == bookingId }
            .orElseThrow { NotFoundException("court_booking_player", participantId) }
        if (p.userId == b.userId) {
            throw ConflictException("takeoff.participant.organizer", "The organizer cannot be removed — cancel the booking instead")
        }
        players.delete(p)
        syncBookingPaymentState(b)
        auditService.log(adminId, "court.participant_remove", "court_booking_player", participantId.toString())
        return dtoWithPlayers(b)
    }

    /** P-04 / US-1.5: organizer covers the remaining tranches — booking becomes fully paid. */
    @Transactional
    fun organizerCoversAll(bookingId: UUID, adminId: UUID): BookingDto {
        val b = bookings.findById(bookingId).orElseThrow { NotFoundException("court_booking", bookingId) }
        if (b.status == BookingStatus.CANCELLED) {
            throw ConflictException("takeoff.booking.cancelled", "Booking is cancelled")
        }
        players.findByBookingId(bookingId).forEach { p ->
            if (p.userId != b.userId && p.paymentStatus == PlayerPaymentStatus.PENDING) {
                p.paymentStatus = PlayerPaymentStatus.COVERED
                p.updatedAt = Instant.now()
                players.save(p)
            }
        }
        b.paymentStatus = CourtPaymentStatus.PAID
        b.updatedAt = Instant.now()
        bookings.save(b)
        auditService.log(adminId, "court.cover_all", "court_booking", bookingId.toString())
        return dtoWithPlayers(b)
    }

    /** C-05: cancel with optional wallet refund. */
    @Transactional
    fun cancel(id: UUID, req: CancelBookingRequest, adminId: UUID): BookingDto {
        val b = bookings.findByIdForUpdate(id).orElseThrow { NotFoundException("court_booking", id) }
        if (b.status == BookingStatus.CANCELLED) {
            throw ConflictException("takeoff.booking.already_cancelled", "Booking already cancelled")
        }
        b.status = BookingStatus.CANCELLED
        b.cancelReason = req.reason
        b.cancelledAt = Instant.now()
        b.updatedAt = Instant.now()

        if (req.refundToWallet) {
            val allPlayers = players.findByBookingId(id)
            allPlayers
                // Guests hold no wallet and can never have paid from one.
                .filter {
                    it.userId != null &&
                        it.paymentStatus == PlayerPaymentStatus.PAID &&
                        it.paymentMethod == PlayerPaymentMethod.WALLET
                }
                .forEach { p ->
                    walletService.apply(
                        userId = p.userId!!, delta = p.shareDt,
                        type = WalletEntryType.REFUND,
                        reason = "Court booking cancelled: ${req.reason}",
                        adminId = adminId,
                        refType = "court_booking",
                        refId = b.id.toString(),
                    )
                }
            b.paymentStatus = CourtPaymentStatus.REFUNDED
        }
        bookings.save(b)
        auditService.log(
            adminId, "court.cancel", "court_booking", id.toString(),
            mapOf("reason" to req.reason, "refunded" to req.refundToWallet),
        )
        return dtoWithPlayers(b)
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
        return dtoWithPlayers(b)
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

    /** C-09: update the payment status on an existing booking. */
    @Transactional
    fun updatePaymentStatus(id: UUID, dto: UpdatePaymentStatusRequest): BookingDto {
        val b = bookings.findById(id).orElseThrow { NotFoundException("court_booking", id) }
        b.paymentStatus = dto.paymentStatus
        b.updatedAt = Instant.now()
        bookings.save(b)
        return dtoWithPlayers(b)
    }

    /** US-3.4: who owes unpaid tranches on matches already played, sorted by amount. */
    fun receivables(): List<ReceivableDto> {
        val now = Instant.now()
        val pending = players.findByPaymentStatus(PlayerPaymentStatus.PENDING)
        if (pending.isEmpty()) return emptyList()
        val bookingMap = bookings.findAllById(pending.map { it.bookingId }.toSet()).associateBy { it.id }
        val due = pending.filter { p ->
            val b = bookingMap[p.bookingId]
            b != null && b.status != BookingStatus.CANCELLED && b.startsAt < now
        }
        if (due.isEmpty()) return emptyList()
        // Only member debts can be attributed to an account; guest tranches are
        // settled at reception against the booking itself.
        val owed = due.filter { it.userId != null }
        val userMap = userRepo.findAllById(owed.mapNotNull { it.userId }.toSet()).associateBy { it.id }
        return owed.groupBy { it.userId!! }.map { (uid, rows) ->
            ReceivableDto(
                userId = uid,
                userName = userMap[uid]?.name,
                phone = userMap[uid]?.phone,
                totalDueDt = rows.fold(java.math.BigDecimal.ZERO) { acc, r -> acc + r.shareDt },
                unpaidCount = rows.size,
                oldestDue = rows.mapNotNull { bookingMap[it.bookingId]?.startsAt }.minOrNull(),
                noShowCount = rows.count { it.noShow },
            )
        }.sortedByDescending { it.totalDueDt }
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

    /** Booking DTO with participant rows + display names resolved. */
    private fun dtoWithPlayers(b: CourtBooking): BookingDto {
        val ps = players.findByBookingId(b.id)
        val ids = (ps.map { it.userId } + listOfNotNull(b.userId)).toSet()
        val names = if (ids.isEmpty()) emptyMap()
        else userRepo.findAllById(ids).associate { it.id to it.name }
        return BookingDto.from(b, names[b.userId], ps, names)
    }

    /** SHARE booking flips to PAID once all 4 tranches are settled (auto, reversible via P-02). */
    private fun syncBookingPaymentState(b: CourtBooking) {
        if (b.mode != BookingMode.SHARE || b.paymentStatus == CourtPaymentStatus.REFUNDED) return
        val ps = players.findByBookingId(b.id)
        val allSettled = ps.size >= BookingDto.SHARE_SLOTS &&
            ps.none { it.paymentStatus == PlayerPaymentStatus.PENDING }
        val next = if (allSettled) CourtPaymentStatus.PAID else CourtPaymentStatus.PENDING
        if (b.paymentStatus != next) {
            b.paymentStatus = next
            b.updatedAt = Instant.now()
            bookings.save(b)
        }
    }

    private fun CourtPaymentMethod.toPlayerMethod(): PlayerPaymentMethod? = when (this) {
        CourtPaymentMethod.D17 -> PlayerPaymentMethod.D17
        CourtPaymentMethod.WALLET -> PlayerPaymentMethod.WALLET
        CourtPaymentMethod.CARD -> PlayerPaymentMethod.CARD
        CourtPaymentMethod.CASH -> PlayerPaymentMethod.CASH
        CourtPaymentMethod.PAY_AT_CLUB -> null
    }

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
