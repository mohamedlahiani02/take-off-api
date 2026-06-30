package tn.takeoff.coaches.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tn.takeoff.coaches.CoachActivity

data class CreateCoachRequest(
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    @field:NotBlank val roleTitle: String,
    @field:NotBlank val bio: String,
    val specs: List<String> = emptyList(),
    val achievements: List<String> = emptyList(),
    val photoUrl: String? = null,
    val displayOrder: Int = 0,
    val showOnPadelPreview: Boolean = false,
    val active: Boolean = true,
    @field:NotNull val activity: CoachActivity,
)

data class UpdateCoachRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val roleTitle: String? = null,
    val bio: String? = null,
    val specs: List<String>? = null,
    val achievements: List<String>? = null,
    val photoUrl: String? = null,
    val displayOrder: Int? = null,
    val showOnPadelPreview: Boolean? = null,
    val active: Boolean? = null,
    val activity: CoachActivity? = null,
)
