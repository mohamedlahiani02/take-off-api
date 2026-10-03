package tn.takeoff.admin.classes

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.admin.users.AdminUserService
import tn.takeoff.admin.users.CreateGhostRequest
import tn.takeoff.classes.*
import tn.takeoff.coaches.CoachRepository
import tn.takeoff.packs.CreditEntryType
import tn.takeoff.packs.PackCreditLedger
import tn.takeoff.packs.PackCreditLedgerRepository
import tn.takeoff.packs.PackTypeRepository
import tn.takeoff.packs.UserPackRepository
import tn.takeoff.packs.UserPackStatus
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ClassBookingHistoryDto(
    val id: UUID,
    val className: String,
    val level: String?,
    val startsAt: Instant,
    val durationMin: Int,
    val instructorName: String?,
    val status: String,
    val priceDt: BigDecimal,
    val paidWith: String,
    val createdAt: Instant,
)

data class SessionDetail(
    val session: ClassSession,
    val bookings: List<ClassBooking>,
    val bookedCount: Long,
    val waitlistCount: Long,
    val proposedCount: Long,
)

@Service
class AdminClassService(
    private val types: ClassTypeRepository,
    private val sessions: ClassSessionRepository,
    private val bookings: ClassBookingRepository,
    private val userPacks: UserPackRepository,
    private val ledger: PackCreditLedgerRepository,
    private val adminUserService: AdminUserService,
    private val auditService: AuditService,
    private val coaches: CoachRepository,
    private val walletLedger: tn.takeoff.users.WalletLedgerRepository,
    private val userRepo: tn.takeoff.users.UserRepository,
    private val packTypes: PackTypeRepository,
    private val walletService: WalletService,
    private val em: EntityManager,
    private val waitlistService: ClassWaitlistService,
) {
    /**
     * Bookings the old logic marked BOOKED/paidWith=SINGLE without ever
     * collecting anything (no wallet debit exists for them). Read-only: this
     * never charges retroactively — a member never gets a surprise debit for
     * a spot they were told, at the time, was simply theirs. It only surfaces
     * the list so the club can decide case by case (waive, invoice, call).
     */
    fun unpaidLegacySingleBookings(): List<tn.takeoff.admin.classes.UnpaidLegacyBookingDto> {
        val candidates = bookings.findByPaidWithAndStatusIn(
            tn.takeoff.classes.PaidWith.SINGLE,
            listOf(tn.takeoff.classes.ClassBookingStatus.BOOKED, tn.takeoff.classes.ClassBookingStatus.ATTENDED),
        ).filter { it.priceDt > java.math.BigDecimal.ZERO && it.userId != null }

        val unpaid = candidates.filter { b ->
            !walletLedger.existsByUserIdAndTypeAndRefTypeAndRefId(
                b.userId!!, tn.takeoff.users.WalletEntryType.PAYMENT, "class_booking", b.id.toString(),
            )
        }
        if (unpaid.isEmpty()) return emptyList()

        val sessionIds = unpaid.map { it.sessionId }.toSet()
        val sessionsById = sessions.findAllById(sessionIds).associateBy { it.id }
        val userIds = unpaid.mapNotNull { it.userId }.toSet()
        val usersById = userRepo.findAllById(userIds).associateBy { it.id }

        return unpaid.map { b ->
            val session = sessionsById[b.sessionId]
            val user = usersById[b.userId]
            tn.takeoff.admin.classes.UnpaidLegacyBookingDto(
                bookingId = b.id, sessionId = b.sessionId, startsAt = session?.startsAt,
                userId = b.userId, userName = user?.name, userPhone = user?.phone,
                priceDt = b.priceDt, createdAt = b.createdAt,
            )
        }
    }

    // ── class types ──
    fun listTypes(): List<ClassType> = types.findAllByOrderByDisplayOrder()

    @Transactional
    fun createType(req: ClassTypeRequest, adminId: UUID): ClassType {
        val t = ClassType(
            name = req.name, level = req.level, durationMin = req.durationMin, description = req.description,
            photoUrl = req.photoUrl, defaultPriceDt = req.defaultPriceDt, displayOrder = req.displayOrder, active = req.active,
        )
        types.save(t)
        auditService.log(adminId, "class.type_create", "class_type", t.id.toString())
        return t
    }

    @Transactional
    fun updateType(id: UUID, req: ClassTypeRequest, adminId: UUID): ClassType {
        val t = types.findById(id).orElseThrow { NotFoundException("class_type", id) }
        t.name = req.name; t.level = req.level; t.durationMin = req.durationMin; t.description = req.description
        t.photoUrl = req.photoUrl; t.defaultPriceDt = req.defaultPriceDt; t.displayOrder = req.displayOrder; t.active = req.active
        types.save(t)
        auditService.log(adminId, "class.type_update", "class_type", id.toString())
        return t
    }

    // ── sessions ──
    fun calendar(from: Instant, to: Instant): List<SessionDetail> =
        sessions.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(from, to).map { detailOf(it) }

    fun sessionDetail(id: UUID): SessionDetail {
        val s = sessions.findById(id).orElseThrow { NotFoundException("class_session", id) }
        return detailOf(s)
    }

    private fun detailOf(s: ClassSession) = SessionDetail(
        session = s,
        bookings = bookings.findBySessionId(s.id),
        bookedCount = bookings.countBySessionIdAndStatus(s.id, ClassBookingStatus.BOOKED),
        waitlistCount = bookings.countBySessionIdAndStatus(s.id, ClassBookingStatus.WAITLIST),
        proposedCount = bookings.countBySessionIdAndStatus(s.id, ClassBookingStatus.PROPOSED),
    )

    @Transactional
    fun createSession(req: SessionRequest, adminId: UUID): ClassSession {
        types.findById(req.classTypeId).orElseThrow { NotFoundException("class_type", req.classTypeId) }
        val s = ClassSession(
            classTypeId = req.classTypeId, instructorId = req.instructorId, startsAt = req.startsAt,
            durationMin = req.durationMin, maxSpots = req.maxSpots, priceDt = req.priceDt, createdByAdminId = adminId,
        )
        sessions.save(s)
        auditService.log(adminId, "class.session_create", "class_session", s.id.toString())
        return s
    }

    @Transactional
    fun updateSession(id: UUID, req: SessionRequest, adminId: UUID): ClassSession {
        val s = sessions.findById(id).orElseThrow { NotFoundException("class_session", id) }
        s.classTypeId = req.classTypeId; s.instructorId = req.instructorId; s.startsAt = req.startsAt
        s.durationMin = req.durationMin; s.maxSpots = req.maxSpots; s.priceDt = req.priceDt; s.updatedAt = Instant.now()
        sessions.save(s)
        auditService.log(adminId, "class.session_update", "class_session", id.toString())
        return s
    }

    /**
     * E-03: cancel a whole session (club-initiated, distinct from a member
     * cancelling their own booking in MemberClassController.cancel).
     *
     * For each SINGLE-paid booking still holding a spot: more than 24h before
     * the session's start, the wallet is refunded automatically and for real
     * (WalletService.applyOnce, keyed on this booking id + a dedicated
     * refType — a retried/replayed cancelSession call cannot double-refund).
     * Inside 24h, no automatic refund happens; the booking is flagged
     * (refund_pending) for an admin to settle by hand — see
     * pendingManualRefunds(). Already-cancelled bookings are skipped (status
     * is no longer BOOKED/WAITLIST), which also makes a retried call a no-op
     * for them on top of applyOnce's own idempotency.
     */
    @Transactional
    fun cancelSession(id: UUID, adminId: UUID): ClassSession {
        val s = sessions.findById(id).orElseThrow { NotFoundException("class_session", id) }
        s.status = SessionStatus.CANCELLED; s.updatedAt = Instant.now()
        sessions.save(s)
        val moreThan24hOut = ChronoUnit.HOURS.between(Instant.now(), s.startsAt) >= 24
        bookings.findBySessionId(id).forEach {
            // PROPOSED is included: a member mid-hold on a session the club
            // just cancelled was never charged (propose() charges nothing),
            // so this only needs to release the hold, same as WAITLIST.
            if (it.status == ClassBookingStatus.BOOKED || it.status == ClassBookingStatus.WAITLIST ||
                it.status == ClassBookingStatus.PROPOSED) {
                if (it.status == ClassBookingStatus.BOOKED && it.paidWith == PaidWith.PACK && it.userPackId != null) {
                    userPacks.findById(it.userPackId!!).ifPresent { up ->
                        val restored = (up.creditsRemaining ?: 0) + 1
                        up.creditsRemaining = restored
                        if (up.status == UserPackStatus.EXPIRED) up.status = UserPackStatus.ACTIVE
                        userPacks.save(up)
                        ledger.save(PackCreditLedger(
                            userPackId = up.id, delta = 1,
                            type = CreditEntryType.REFUND, reason = "session_cancel_refund",
                            refType = "class_booking", refId = it.id.toString(),
                        ))
                    }
                } else if (it.status == ClassBookingStatus.BOOKED && it.paidWith == PaidWith.SINGLE &&
                    it.userId != null && it.priceDt > BigDecimal.ZERO
                ) {
                    if (moreThan24hOut) {
                        walletService.applyOnce(
                            userId = it.userId!!, delta = it.priceDt, type = WalletEntryType.REFUND,
                            reason = "session_cancel_refund", refType = "class_booking_session_cancel",
                            refId = it.id.toString(),
                        )
                    } else {
                        it.refundPending = true
                    }
                }
                it.status = ClassBookingStatus.CANCELLED; it.updatedAt = Instant.now(); bookings.save(it)
            }
        }
        auditService.log(adminId, "class.session_cancel", "class_session", id.toString())
        return s
    }

    /**
     * SINGLE-paid bookings the club cancelled (via cancelSession) less than
     * 24h before the session started: policy leaves these unrefunded
     * automatically so an admin can settle them by hand. Read-only, same
     * shape as unpaidLegacySingleBookings — listing this never refunds
     * anything by itself.
     */
    fun pendingManualRefunds(): List<UnpaidLegacyBookingDto> {
        val pending = bookings.findByRefundPendingTrue()
            .filter { it.priceDt > BigDecimal.ZERO && it.userId != null }
        if (pending.isEmpty()) return emptyList()

        val sessionIds = pending.map { it.sessionId }.toSet()
        val sessionsById = sessions.findAllById(sessionIds).associateBy { it.id }
        val userIds = pending.mapNotNull { it.userId }.toSet()
        val usersById = userRepo.findAllById(userIds).associateBy { it.id }

        return pending.map { b ->
            val session = sessionsById[b.sessionId]
            val user = usersById[b.userId]
            UnpaidLegacyBookingDto(
                bookingId = b.id, sessionId = b.sessionId, startsAt = session?.startsAt,
                userId = b.userId, userName = user?.name, userPhone = user?.phone,
                priceDt = b.priceDt, createdAt = b.createdAt,
            )
        }
    }

    // ── bookings / attendance ──
    @Transactional
    fun addStudent(sessionId: UUID, req: AddStudentRequest, adminId: UUID): ClassBooking {
        val s = sessions.findByIdForUpdate(sessionId).orElseThrow { NotFoundException("class_session", sessionId) }
        val userId = when {
            req.userId != null -> req.userId
            !req.ghostName.isNullOrBlank() && !req.ghostPhone.isNullOrBlank() ->
                adminUserService.createGhost(CreateGhostRequest(req.ghostName, req.ghostPhone), adminId).id
            else -> throw BadRequestException("takeoff.class.no_user", "Provide userId or ghostName + ghostPhone")
        }
        val booked = bookings.countBySessionIdAndStatus(sessionId, ClassBookingStatus.BOOKED)
        val full = booked >= s.maxSpots
        val b = ClassBooking(
            sessionId = sessionId, userId = userId,
            status = if (full) ClassBookingStatus.WAITLIST else ClassBookingStatus.BOOKED,
            waitlistPosition = if (full) (bookings.countBySessionIdAndStatus(sessionId, ClassBookingStatus.WAITLIST) + 1).toInt() else null,
            priceDt = s.priceDt, createdByAdminId = adminId,
        )
        bookings.save(b)
        auditService.log(adminId, "class.add_student", "class_booking", b.id.toString())
        return b
    }

    @Transactional
    fun removeStudent(bookingId: UUID, adminId: UUID) {
        val b = bookings.findById(bookingId).orElseThrow { NotFoundException("class_booking", bookingId) }
        if (b.status == ClassBookingStatus.BOOKED && b.paidWith == PaidWith.PACK && b.userPackId != null) {
            userPacks.findById(b.userPackId!!).ifPresent { up ->
                val restored = (up.creditsRemaining ?: 0) + 1
                up.creditsRemaining = restored
                if (up.status == UserPackStatus.EXPIRED) up.status = UserPackStatus.ACTIVE
                userPacks.save(up)
                ledger.save(PackCreditLedger(
                    userPackId = up.id, delta = 1,
                    type = CreditEntryType.REFUND, reason = "admin_remove",
                    refType = "class_booking", refId = b.id.toString(),
                ))
            }
        }
        // A PROPOSED booking was never charged — removing it only needs to
        // release the hold and let the next waitlisted member have a go.
        val wasProposed = b.status == ClassBookingStatus.PROPOSED
        b.status = ClassBookingStatus.CANCELLED; b.proposalExpiresAt = null; b.updatedAt = Instant.now()
        bookings.save(b)
        if (wasProposed) waitlistService.proposeNext(b.sessionId)
        auditService.log(adminId, "class.remove_student", "class_booking", bookingId.toString())
    }

    /**
     * E-07: offer a waitlisted booking the next open spot.
     *
     * This no longer charges anyone. A promotion now only *proposes* the
     * spot (PROPOSED, time-boxed) — nothing is charged until the member
     * themselves confirms via MemberClassController.confirmProposal, which
     * is the only code path that ever moves a booking to BOOKED. Joining a
     * waitlist promises the member "nothing will be debited"; an admin
     * click used to silently break that promise by charging the wallet with
     * no fresh consent. See ClassWaitlistService for the full propose /
     * confirm / decline / expire state machine.
     */
    @Transactional
    fun promote(bookingId: UUID, adminId: UUID): ClassBooking = waitlistService.propose(bookingId, adminId)

    /**
     * E-09: record attendance. This endpoint's only job is recording whether
     * a member who held a real (already-paid) spot showed up — never a back
     * door into BOOKED/PROPOSED state transitions, which must only ever
     * happen through propose()/confirmProposal()/declineProposal() so a spot
     * can never be granted without going through real payment resolution and
     * the member's own consent. ATTENDED/ABSENT are the only statuses this
     * can set.
     */
    @Transactional
    fun setAttendance(bookingId: UUID, status: ClassBookingStatus, adminId: UUID): ClassBooking {
        if (status != ClassBookingStatus.ATTENDED && status != ClassBookingStatus.ABSENT)
            throw BadRequestException("takeoff.class.invalid_attendance_status", "Attendance can only be set to ATTENDED or ABSENT")
        val b = bookings.findByIdForUpdate(bookingId).orElseThrow { NotFoundException("class_booking", bookingId) }
        if (b.status != ClassBookingStatus.BOOKED)
            throw BadRequestException("takeoff.class.not_booked", "Only a confirmed booking can have attendance recorded")
        b.status = status; b.updatedAt = Instant.now()
        bookings.save(b)
        auditService.log(adminId, "class.attendance", "class_booking", bookingId.toString(), mapOf("status" to status.name))
        return b
    }

    fun classHistoryForUser(userId: UUID): List<ClassBookingHistoryDto> {
        val typeCache = mutableMapOf<UUID, ClassType>()
        val sessionCache = mutableMapOf<UUID, ClassSession>()
        val coachCache = mutableMapOf<UUID, String>()
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).map { b ->
            val session = sessionCache.getOrPut(b.sessionId) {
                sessions.findById(b.sessionId).orElse(null)
            } ?: return@map null
            val classType = typeCache.getOrPut(session.classTypeId) {
                types.findById(session.classTypeId).orElse(null)
            } ?: return@map null
            val instructorName = session.instructorId?.let { iid ->
                coachCache.getOrPut(iid) {
                    coaches.findById(iid).map { c -> "${c.firstName} ${c.lastName}" }.orElse(null) ?: ""
                }.takeIf { it.isNotEmpty() }
            }
            ClassBookingHistoryDto(
                id = b.id,
                className = classType.name,
                level = classType.level,
                startsAt = session.startsAt,
                durationMin = session.durationMin,
                instructorName = instructorName,
                status = b.status.name,
                priceDt = b.priceDt,
                paidWith = b.paidWith.name,
                createdAt = b.createdAt,
            )
        }.filterNotNull()
    }
}

