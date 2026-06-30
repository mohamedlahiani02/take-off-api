package tn.takeoff.coaches

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CoachRepository : JpaRepository<Coach, UUID>, CoachGateway {
    override fun findByActivityAndActiveOrderByDisplayOrder(activity: CoachActivity, active: Boolean): List<Coach>
    override fun findByActivityAndActiveAndShowOnPadelPreviewOrderByDisplayOrder(
        activity: CoachActivity,
        active: Boolean,
        showOnPadelPreview: Boolean,
    ): List<Coach>
}
