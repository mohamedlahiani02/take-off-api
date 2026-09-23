package tn.takeoff.coaches

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.coaches.dto.CoachDto
import tn.takeoff.coaches.dto.CreateCoachRequest
import tn.takeoff.coaches.dto.UpdateCoachRequest
import tn.takeoff.common.errors.NotFoundException
import java.time.Instant
import java.util.UUID

@Service
class CoachService(
    private val repo: CoachRepository,
    private val auditService: AuditService,
) {

    fun listAll(): List<CoachDto> = repo.findAll()
        .sortedWith(compareBy({ it.activity }, { it.displayOrder }))
        .map(CoachDto::from)

    fun listPublic(activity: CoachActivity, preview: Boolean): List<CoachDto> {
        val coaches = if (preview) {
            repo.findByActivityAndActiveAndShowOnPadelPreviewOrderByDisplayOrder(activity, true, true)
                .take(4)
        } else {
            repo.findByActivityAndActiveOrderByDisplayOrder(activity, true)
        }
        return coaches.map(CoachDto::from)
    }

    /** One coach, for the public detail page. Inactive coaches are not exposed. */
    fun getPublic(id: UUID): CoachDto {
        val coach = repo.findById(id)
            .filter { it.active }
            .orElseThrow { NotFoundException("coach", id) }
        return CoachDto.from(coach)
    }

    @Transactional
    fun create(dto: CreateCoachRequest, adminId: UUID): CoachDto {
        val coach = Coach(
            firstName = dto.firstName,
            lastName = dto.lastName,
            roleTitle = dto.roleTitle,
            bio = dto.bio,
            specs = dto.specs.toTypedArray(),
            achievements = dto.achievements.toTypedArray(),
            photoUrl = dto.photoUrl,
            displayOrder = dto.displayOrder,
            showOnPadelPreview = dto.showOnPadelPreview,
            active = dto.active,
            activity = dto.activity,
        )
        val saved = repo.save(coach)
        auditService.log(adminId, "coach.create", "coach", saved.id.toString())
        return CoachDto.from(saved)
    }

    @Transactional
    fun update(id: UUID, dto: UpdateCoachRequest, adminId: UUID): CoachDto {
        val coach = repo.findById(id).orElseThrow { NotFoundException("coach", id) }
        dto.firstName?.let { coach.firstName = it }
        dto.lastName?.let { coach.lastName = it }
        dto.roleTitle?.let { coach.roleTitle = it }
        dto.bio?.let { coach.bio = it }
        dto.specs?.let { coach.specs = it.toTypedArray() }
        dto.achievements?.let { coach.achievements = it.toTypedArray() }
        dto.photoUrl?.let { coach.photoUrl = it }
        dto.displayOrder?.let { coach.displayOrder = it }
        dto.showOnPadelPreview?.let { coach.showOnPadelPreview = it }
        dto.active?.let { coach.active = it }
        dto.activity?.let { coach.activity = it }
        coach.updatedAt = Instant.now()
        val saved = repo.save(coach)
        auditService.log(adminId, "coach.update", "coach", id.toString())
        return CoachDto.from(saved)
    }

    @Transactional
    fun delete(id: UUID, adminId: UUID) {
        val coach = repo.findById(id).orElseThrow { NotFoundException("coach", id) }
        repo.delete(coach)
        auditService.log(adminId, "coach.delete", "coach", id.toString())
    }

    @Transactional
    fun reorder(ids: List<UUID>, adminId: UUID) {
        val coaches = repo.findAllById(ids).associateBy { it.id }
        val updated = ids.mapIndexedNotNull { index, id ->
            coaches[id]?.also { it.displayOrder = index + 1; it.updatedAt = Instant.now() }
        }
        updated.forEach { repo.save(it) }
        auditService.log(adminId, "coach.reorder", "coach", null)
    }

    @Transactional
    fun updatePhoto(id: UUID, photoUrl: String, adminId: UUID): CoachDto {
        val coach = repo.findById(id).orElseThrow { NotFoundException("coach", id) }
        coach.photoUrl = photoUrl
        coach.updatedAt = Instant.now()
        val saved = repo.save(coach)
        auditService.log(adminId, "coach.photo_update", "coach", id.toString())
        return CoachDto.from(saved)
    }
}
