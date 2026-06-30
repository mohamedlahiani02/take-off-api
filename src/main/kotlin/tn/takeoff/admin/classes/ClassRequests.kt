package tn.takeoff.admin.classes

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tn.takeoff.classes.ClassBookingStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ClassTypeRequest(
    @field:NotBlank val name: String,
    val level: String? = null,
    val durationMin: Int = 50,
    val description: String? = null,
    val photoUrl: String? = null,
    val defaultPriceDt: BigDecimal = BigDecimal("35"),
    val displayOrder: Int = 0,
    val active: Boolean = true,
)

data class SessionRequest(
    @field:NotNull val classTypeId: UUID,
    val instructorId: UUID? = null,
    @field:NotNull val startsAt: Instant,
    val durationMin: Int = 50,
    val maxSpots: Int = 8,
    @field:NotNull val priceDt: BigDecimal,
)

data class AddStudentRequest(
    val userId: UUID? = null,
    val ghostName: String? = null,
    val ghostPhone: String? = null,
)

data class AttendanceRequest(@field:NotNull val status: ClassBookingStatus)
