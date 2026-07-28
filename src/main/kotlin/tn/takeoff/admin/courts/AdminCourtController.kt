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

    /** US-3.4: outstanding tranches per member. */
    @GetMapping("/receivables")
    fun receivables(): List<ReceivableDto> = service.receivables()

    /** P-00: booking detail with participant slots. */
    @GetMapping("/bookings/{id}")
    fun bookingDetail(@PathVariable id: UUID): BookingDto = service.bookingDetail(id)

    /** P-01: attach a participant (existing member or new member inline). */
    @PostMapping("/bookings/{id}/participants")
    fun addParticipant(
        @PathVariable id: UUID,
        @Valid @RequestBody req: AddParticipantRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): BookingDto = service.addParticipant(id, req, admin.adminId)

    /** P-02: settle / adjust one tranche (payment status, method, no-show). */
    @PatchMapping("/bookings/{id}/participants/{participantId}")
    fun updateParticipant(
        @PathVariable id: UUID,
        @PathVariable participantId: UUID,
        @RequestBody req: UpdateParticipantRequest,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): BookingDto = service.updateParticipant(id, participantId, req, admin.adminId)

    /** P-03: detach a participant. */
    @DeleteMapping("/bookings/{id}/participants/{participantId}")
    fun removeParticipant(
        @PathVariable id: UUID,
        @PathVariable participantId: UUID,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): BookingDto = service.removeParticipant(id, participantId, admin.adminId)

    /** P-04: organizer covers the remaining tranches. */
    @PostMapping("/bookings/{id}/cover-all")
    fun coverAll(
        @PathVariable id: UUID,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): BookingDto = service.organizerCoversAll(id, admin.adminId)

    /** C-09: update payment status. */
    @PatchMapping("/bookings/{id}/payment")
    fun updatePaymentStatus(
        @PathVariable id: UUID,
        @RequestBody dto: UpdatePaymentStatusRequest,
    ): BookingDto = service.updatePaymentStatus(id, dto)

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
