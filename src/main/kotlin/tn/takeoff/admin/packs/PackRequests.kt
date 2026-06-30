package tn.takeoff.admin.packs

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tn.takeoff.packs.PackActivity
import java.math.BigDecimal
import java.util.UUID

data class PackTypeRequest(
    @field:NotBlank val name: String,
    @field:NotNull val activity: PackActivity,
    @field:NotNull val priceDt: BigDecimal,
    val creditCount: Int? = null,
    val unlimited: Boolean = false,
    val validityMonths: Int = 12,
    val displayOrder: Int = 0,
    val active: Boolean = true,
)

/** F-04: assign a pack to a user (cash at reception). */
data class AssignPackRequest(
    @field:NotNull val userId: UUID,
    @field:NotNull val packTypeId: UUID,
)

/** F-05: extend expiry. */
data class ExtendRequest(
    @field:NotNull val newExpiresAt: java.time.Instant,
    @field:NotBlank val reason: String,
)

/** F-06: add/remove credits. */
data class CreditAdjustRequest(
    @field:NotNull val delta: Int,
    @field:NotBlank val reason: String,
)
