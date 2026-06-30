package tn.takeoff.admin.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class AdminLoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

data class AdminLoginResponse(
    val token: String,
    val adminId: UUID,
    val name: String,
    val role: AdminRole,
)
