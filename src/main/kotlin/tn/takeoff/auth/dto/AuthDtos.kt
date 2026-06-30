package tn.takeoff.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import tn.takeoff.users.User
import tn.takeoff.users.UserRole
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
    @field:NotBlank val name: String,
    @field:NotBlank @field:Pattern(
        regexp = "^\\+216[0-9]{8}$",
        message = "Phone must be a valid +216 number (8 digits)",
    ) val phone: String,
    val tracks: List<String> = emptyList(),
)

data class LoginRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\+?[0-9\\s-]{8,15}$", message = "Invalid phone number")
    val phone: String,
    @field:NotBlank val password: String,
)

data class ForgotPasswordRequest(
    @field:NotBlank @field:Email val email: String,
)

data class ResetPasswordRequest(
    @field:NotBlank val token: String,
    @field:NotBlank @field:Size(min = 8) val newPassword: String,
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

data class AuthResponse(
    val tokens: TokenPair,
    val user: UserDto,
)

data class UserDto(
    val id: UUID,
    val email: String,
    val name: String,
    val phone: String?,
    val tracks: List<String>,
    val role: UserRole,
    val walletDt: BigDecimal,
    val points: Int,
    val createdAt: Instant,
) {
    companion object {
        fun from(u: User) = UserDto(
            id = u.id,
            email = u.email,
            name = u.name,
            phone = u.phone,
            tracks = u.tracks.toList(),
            role = u.role,
            walletDt = u.walletDt,
            points = u.points,
            createdAt = u.createdAt,
        )
    }
}

data class UpdateMeRequest(
    val name: String? = null,
    val phone: String? = null,
    val tracks: List<String>? = null,
)
