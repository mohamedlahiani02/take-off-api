package tn.takeoff.coaching

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

data class CoachingInquiryRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val phone: String,
    @field:NotBlank @field:Email val email: String,
    val lessonTypes: List<String> = emptyList(),
    val availabilityGrid: Map<String, Boolean> = emptyMap(),
    val level: String? = null,
    val notes: String? = null,
)

@RestController
@RequestMapping("/api/v1/coaching")
class CoachingController(private val repo: CoachingGateway) {

    @PostMapping("/inquiry")
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(@Valid @RequestBody dto: CoachingInquiryRequest): Map<String, String> {
        val entity = CoachingInquiry(
            name = dto.name,
            phone = dto.phone,
            email = dto.email,
            lessonTypes = dto.lessonTypes.toTypedArray(),
            availabilityGrid = dto.availabilityGrid,
            level = dto.level,
            notes = dto.notes,
        )
        repo.save(entity)
        return mapOf("message" to "Inquiry received", "id" to entity.id.toString())
    }
}
