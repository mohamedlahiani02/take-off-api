package tn.takeoff.admin.tournaments

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tn.takeoff.tournaments.DiscountType
import tn.takeoff.tournaments.FieldType
import tn.takeoff.tournaments.PaymentRule
import tn.takeoff.tournaments.RegStatus
import tn.takeoff.tournaments.RegistrationMode
import tn.takeoff.tournaments.TournamentCategory
import tn.takeoff.tournaments.TournamentFormat
import tn.takeoff.tournaments.TournamentStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TournamentRequest(
    @field:NotBlank val title: String,
    val description: String? = null,
    val bannerUrl: String? = null,
    @field:NotNull val format: TournamentFormat,
    @field:NotNull val category: TournamentCategory,
    @field:NotNull val startsAt: Instant,
    val endsAt: Instant? = null,
    val entryFeeDt: BigDecimal = BigDecimal.ZERO,
    val prize: String? = null,
    val maxParticipants: Int? = null,
    val registrationDeadline: Instant? = null,
    val registrationMode: RegistrationMode = RegistrationMode.OPEN,
    val autoWaitlist: Boolean = false,
    val manualValidation: Boolean = false,
    val paymentRule: PaymentRule = PaymentRule.BOTH,
)

data class StatusRequest(@field:NotNull val status: TournamentStatus)

data class FieldRequest(
    @field:NotNull val fieldType: FieldType,
    @field:NotBlank val label: String,
    val helpText: String? = null,
    val required: Boolean = false,
    val options: Map<String, Any>? = null,
    val displayOrder: Int = 0,
)

data class PricingRequest(
    @field:NotBlank val label: String,
    @field:NotNull val priceDt: BigDecimal,
    val displayOrder: Int = 0,
)

data class PromoCodeRequest(
    @field:NotBlank val code: String,
    @field:NotNull val discountType: DiscountType,
    @field:NotNull val discountValue: BigDecimal,
    val maxUses: Int? = null,
    val expiresAt: Instant? = null,
)

/** D-45: admin registers a user (existing or ghost) directly. */
data class ManualRegisterRequest(
    val userId: UUID? = null,
    val ghostName: String? = null,
    val ghostPhone: String? = null,
    val categoryLabel: String? = null,
    val answers: Map<String, Any> = emptyMap(),
)

data class RegStatusRequest(
    @field:NotNull val status: RegStatus,
    val refund: Boolean = false,
)
