package tn.takeoff.admin.courts

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/courts")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','RECEPTION')")
class AdminCourtController(private val service: AdminCourtService) {

    /** C-01: calendar window. `from`/`to` are ISO-8601 instants. */
    @GetMapping("/calendar")
    fun calendar(
        @RequestParam from: Instant,
        @RequestParam to: Instant,
    ): CalendarDto = service.calendar(from, to)

    /** C-02/03/04: book on behalf. */
    @PostMapping("/bookings")
    fun book(
        @Valid @RequestBody req: CreateBookingRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): BookingDto = service.createBooking(req, admin.adminId)

    /** C-05: cancel. */
    @PostMapping("/bookings/{id}/cancel")
    fun cancel(
        @PathVariable id: UUID,
        @Valid @RequestBody req: CancelBookingRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): BookingDto = service.cancel(id, req, admin.adminId)

    /** C-06: reschedule. */
    @PostMapping("/bookings/{id}/reschedule")
    fun reschedule(
        @PathVariable id: UUID,
        @Valid @RequestBody req: RescheduleRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): BookingDto = service.reschedule(id, req, admin.adminId)

    /** C-07/08: create a block. */
    @PostMapping("/blocks")
    fun block(
        @Valid @RequestBody req: CreateBlockRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): BlockDto = service.createBlock(req, admin.adminId)

    /** C-07: remove a block. */
    @DeleteMapping("/blocks/{id}")
    fun unblock(
        @PathVariable id: UUID,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ) = service.deleteBlock(id, admin.adminId)
}
