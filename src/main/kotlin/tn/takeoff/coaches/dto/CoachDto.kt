package tn.takeoff.coaches.dto

import tn.takeoff.coaches.Coach
import tn.takeoff.coaches.CoachActivity
import java.time.Instant
import java.util.UUID

data class CoachDto(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val roleTitle: String,
    val bio: String,
    val specs: List<String>,
    val achievements: List<String>,
    val photoUrl: String?,
    val displayOrder: Int,
    val showOnPadelPreview: Boolean,
    val active: Boolean,
    val activity: CoachActivity,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(coach: Coach) = CoachDto(
            id = coach.id,
            firstName = coach.firstName,
            lastName = coach.lastName,
            roleTitle = coach.roleTitle,
            bio = coach.bio,
            specs = coach.specs.toList(),
            achievements = coach.achievements.toList(),
            photoUrl = coach.photoUrl,
            displayOrder = coach.displayOrder,
            showOnPadelPreview = coach.showOnPadelPreview,
            active = coach.active,
            activity = coach.activity,
            createdAt = coach.createdAt,
            updatedAt = coach.updatedAt,
        )
    }
}
