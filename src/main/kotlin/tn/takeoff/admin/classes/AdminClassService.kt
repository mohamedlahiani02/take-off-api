package tn.takeoff.admin.classes

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
import tn.takeoff.packs.UserPackRepository
import tn.takeoff.packs.UserPackStatus
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.ConflictException
import tn.takeoff.common.errors.NotFoundException
import java.math.BigDecimal
import java.time.Instant
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
) {

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

    /** E-03: cancel a session — credits/refunds handled when pack logic lands; marks bookings cancelled. */
    @Transactional
    fun cancelSession(id: UUID, adminId: UUID): ClassSession {
        val s = sessions.findById(id).orElseThrow { NotFoundException("class_session", id) }
        s.status = SessionStatus.CANCELLED; s.updatedAt = Instant.now()
        sessions.save(s)
        bookings.findBySessionId(id).forEach {
            if (it.status == ClassBookingStatus.BOOKED || it.status == ClassBookingStatus.WAITLIST) {
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
                }
                it.status = ClassBookingStatus.CANCELLED; it.updatedAt = Instant.now(); bookings.save(it)
            }
        }
        auditService.log(adminId, "class.session_cancel", "class_session", id.toString())
        return s
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
        b.status = ClassBookingStatus.CANCELLED; b.updatedAt = Instant.now()
        bookings.save(b)
        auditService.log(adminId, "class.remove_student", "class_booking", bookingId.toString())
    }

    /** E-07: promote a waitlisted booking to BOOKED. */
    @Transactional
    fun promote(bookingId: UUID, adminId: UUID): ClassBooking {
        val b = bookings.findById(bookingId).orElseThrow { NotFoundException("class_booking", bookingId) }
        if (b.status != ClassBookingStatus.WAITLIST) throw ConflictException("takeoff.class.not_waitlisted", "Not on the waitlist")
        val session = sessions.findByIdForUpdate(b.sessionId).orElseThrow { NotFoundException("class_session", b.sessionId) }
        val bookedCount = bookings.countBySessionIdAndStatus(b.sessionId, ClassBookingStatus.BOOKED)
        if (bookedCount >= session.maxSpots)
            throw BadRequestException("takeoff.class.full", "Session is already full")
        b.status = ClassBookingStatus.BOOKED; b.waitlistPosition = null; b.updatedAt = Instant.now()
        bookings.save(b)
        auditService.log(adminId, "class.promote", "class_booking", bookingId.toString())
        return b
    }

    /** E-09: record attendance. */
    @Transactional
    fun setAttendance(bookingId: UUID, status: ClassBookingStatus, adminId: UUID): ClassBooking {
        val b = bookings.findById(bookingId).orElseThrow { NotFoundException("class_booking", bookingId) }
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

