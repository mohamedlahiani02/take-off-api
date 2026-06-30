package tn.takeoff.admin.classes

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.admin.users.AdminUserService
import tn.takeoff.admin.users.CreateGhostRequest
import tn.takeoff.classes.*
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.ConflictException
import tn.takeoff.common.errors.NotFoundException
import java.time.Instant
import java.util.UUID

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
    private val adminUserService: AdminUserService,
    private val auditService: AuditService,
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
                it.status = ClassBookingStatus.CANCELLED; it.updatedAt = Instant.now(); bookings.save(it)
            }
        }
        auditService.log(adminId, "class.session_cancel", "class_session", id.toString())
        return s
    }

    // ── bookings / attendance ──
    @Transactional
    fun addStudent(sessionId: UUID, req: AddStudentRequest, adminId: UUID): ClassBooking {
        val s = sessions.findById(sessionId).orElseThrow { NotFoundException("class_session", sessionId) }
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
        b.status = ClassBookingStatus.CANCELLED; b.updatedAt = Instant.now()
        bookings.save(b)
        auditService.log(adminId, "class.remove_student", "class_booking", bookingId.toString())
    }

    /** E-07: promote a waitlisted booking to BOOKED. */
    @Transactional
    fun promote(bookingId: UUID, adminId: UUID): ClassBooking {
        val b = bookings.findById(bookingId).orElseThrow { NotFoundException("class_booking", bookingId) }
        if (b.status != ClassBookingStatus.WAITLIST) throw ConflictException("takeoff.class.not_waitlisted", "Not on the waitlist")
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
}
