package tn.takeoff.admin.classes

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.classes.ClassBooking
import tn.takeoff.classes.ClassSession
import tn.takeoff.classes.ClassType
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/classes")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','RECEPTION')")
class AdminClassController(private val service: AdminClassService) {

    // class types
    @GetMapping("/types") fun listTypes(): List<ClassType> = service.listTypes()

    @PostMapping("/types")
    fun createType(@Valid @RequestBody req: ClassTypeRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): ClassType =
        service.createType(req, a.adminId)

    @PutMapping("/types/{id}")
    fun updateType(@PathVariable id: UUID, @Valid @RequestBody req: ClassTypeRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): ClassType =
        service.updateType(id, req, a.adminId)

    // sessions
    @GetMapping("/sessions")
    fun calendar(@RequestParam from: Instant, @RequestParam to: Instant): List<SessionDetail> = service.calendar(from, to)

    @GetMapping("/sessions/{id}")
    fun sessionDetail(@PathVariable id: UUID): SessionDetail = service.sessionDetail(id)

    @PostMapping("/sessions")
    fun createSession(@Valid @RequestBody req: SessionRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): ClassSession =
        service.createSession(req, a.adminId)

    @PutMapping("/sessions/{id}")
    fun updateSession(@PathVariable id: UUID, @Valid @RequestBody req: SessionRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): ClassSession =
        service.updateSession(id, req, a.adminId)

    @PostMapping("/sessions/{id}/cancel")
    fun cancelSession(@PathVariable id: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims): ClassSession =
        service.cancelSession(id, a.adminId)

    // bookings / attendance
    @PostMapping("/sessions/{id}/students")
    fun addStudent(@PathVariable id: UUID, @Valid @RequestBody req: AddStudentRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): ClassBooking =
        service.addStudent(id, req, a.adminId)

    @DeleteMapping("/bookings/{bookingId}")
    fun removeStudent(@PathVariable bookingId: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims) =
        service.removeStudent(bookingId, a.adminId)

    @PostMapping("/bookings/{bookingId}/promote")
    fun promote(@PathVariable bookingId: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims): ClassBooking =
        service.promote(bookingId, a.adminId)

    @PostMapping("/bookings/{bookingId}/attendance")
    fun attendance(@PathVariable bookingId: UUID, @Valid @RequestBody req: AttendanceRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): ClassBooking =
        service.setAttendance(bookingId, req.status, a.adminId)
}
