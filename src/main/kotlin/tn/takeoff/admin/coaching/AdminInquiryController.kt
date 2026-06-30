package tn.takeoff.admin.coaching

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.auth.JwtService
import tn.takeoff.coaching.CoachingInquiry
import tn.takeoff.coaching.CoachingRepository
import tn.takeoff.coaching.InquiryOutcome
import tn.takeoff.coaching.InquiryStatus
import tn.takeoff.common.errors.NotFoundException
import java.time.Instant
import java.util.UUID

data class InquiryStatusRequest(@field:NotNull val status: InquiryStatus)
data class InquiryAssignRequest(val coachId: UUID?)
data class InquiryNoteRequest(val note: String?)
data class InquiryCloseRequest(@field:NotNull val outcome: InquiryOutcome)

@Service
class AdminInquiryService(
    private val inquiries: CoachingRepository,
    private val auditService: AuditService,
) {
    @Transactional
    fun setStatus(id: UUID, status: InquiryStatus, adminId: UUID): CoachingInquiry {
        val i = inquiries.findById(id).orElseThrow { NotFoundException("inquiry", id) }
        i.status = status; i.updatedAt = Instant.now()
        inquiries.save(i)
        auditService.log(adminId, "inquiry.status", "coaching_inquiry", id.toString(), mapOf("status" to status.name))
        return i
    }

    @Transactional
    fun assign(id: UUID, coachId: UUID?, adminId: UUID): CoachingInquiry {
        val i = inquiries.findById(id).orElseThrow { NotFoundException("inquiry", id) }
        i.assignedCoachId = coachId; i.updatedAt = Instant.now()
        inquiries.save(i)
        auditService.log(adminId, "inquiry.assign", "coaching_inquiry", id.toString())
        return i
    }

    @Transactional
    fun note(id: UUID, note: String?, adminId: UUID): CoachingInquiry {
        val i = inquiries.findById(id).orElseThrow { NotFoundException("inquiry", id) }
        i.adminNote = note; i.updatedAt = Instant.now()
        inquiries.save(i)
        auditService.log(adminId, "inquiry.note", "coaching_inquiry", id.toString())
        return i
    }

    @Transactional
    fun close(id: UUID, outcome: InquiryOutcome, adminId: UUID): CoachingInquiry {
        val i = inquiries.findById(id).orElseThrow { NotFoundException("inquiry", id) }
        i.outcome = outcome; i.status = InquiryStatus.CLOSED; i.updatedAt = Instant.now()
        inquiries.save(i)
        auditService.log(adminId, "inquiry.close", "coaching_inquiry", id.toString(), mapOf("outcome" to outcome.name))
        return i
    }
}

@RestController
@RequestMapping("/api/v1/admin/coaching")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','RECEPTION','COACH')")
class AdminInquiryController(private val service: AdminInquiryService) {

    @PostMapping("/{id}/status")
    fun status(@PathVariable id: UUID, @Valid @RequestBody req: InquiryStatusRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): CoachingInquiry =
        service.setStatus(id, req.status, a.adminId)

    @PostMapping("/{id}/assign")
    fun assign(@PathVariable id: UUID, @RequestBody req: InquiryAssignRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): CoachingInquiry =
        service.assign(id, req.coachId, a.adminId)

    @PostMapping("/{id}/note")
    fun note(@PathVariable id: UUID, @RequestBody req: InquiryNoteRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): CoachingInquiry =
        service.note(id, req.note, a.adminId)

    @PostMapping("/{id}/close")
    fun close(@PathVariable id: UUID, @Valid @RequestBody req: InquiryCloseRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): CoachingInquiry =
        service.close(id, req.outcome, a.adminId)
}
