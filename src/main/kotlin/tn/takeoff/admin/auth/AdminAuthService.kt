package tn.takeoff.admin.auth

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import tn.takeoff.auth.JwtService
import tn.takeoff.common.errors.UnauthorizedException

@Service
class AdminAuthService(
    private val adminRepo: AdminGateway,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {

    fun login(email: String, password: String): AdminLoginResponse {
        val admin = adminRepo.findByEmail(email.trim().lowercase())
            .orElseThrow { UnauthorizedException("takeoff.admin.invalid_credentials", "Invalid credentials") }
        if (!admin.active)
            throw UnauthorizedException("takeoff.admin.inactive", "Account inactive")
        if (!passwordEncoder.matches(password, admin.passwordHash))
            throw UnauthorizedException("takeoff.admin.invalid_credentials", "Invalid credentials")
        val token = jwtService.issueAdminAccessToken(
            JwtService.AdminClaims(admin.id, admin.email, admin.name, admin.role)
        )
        return AdminLoginResponse(token = token, adminId = admin.id, name = admin.name, role = admin.role)
    }
}
