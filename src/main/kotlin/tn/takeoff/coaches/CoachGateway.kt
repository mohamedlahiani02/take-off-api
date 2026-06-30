package tn.takeoff.coaches

interface CoachGateway {
    fun findByActivityAndActiveOrderByDisplayOrder(activity: CoachActivity, active: Boolean): List<Coach>
    fun findByActivityAndActiveAndShowOnPadelPreviewOrderByDisplayOrder(
        activity: CoachActivity,
        active: Boolean,
        showOnPadelPreview: Boolean,
    ): List<Coach>
}
