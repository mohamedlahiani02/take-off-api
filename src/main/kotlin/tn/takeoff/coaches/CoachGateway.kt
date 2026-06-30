package tn.takeoff.coaches

import java.util.Optional
import java.util.UUID

interface CoachGateway {
    fun findByActivityAndActiveOrderByDisplayOrder(activity: CoachActivity, active: Boolean): List<Coach>
    fun findByActivityAndActiveAndShowOnPadelPreviewOrderByDisplayOrder(
        activity: CoachActivity,
        active: Boolean,
        showOnPadelPreview: Boolean,
    ): List<Coach>
    fun findById(id: UUID): Optional<Coach>
    fun save(coach: Coach): Coach
    fun delete(coach: Coach)
    fun saveAll(coaches: List<Coach>): List<Coach>
    fun findAllById(ids: Iterable<UUID>): List<Coach>
    fun findAll(): List<Coach>
}
