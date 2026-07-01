package tn.takeoff.admin.auth

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService

@RestController
@RequestMapping("/api/v1/admin/auth")
class AdminAuthController(private val service: AdminAuthService) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: AdminLoginRequest): AdminLoginResponse =
        service.login(dto.email, dto.password)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal claims: JwtService.AdminClaims) {
        // JWT TTL-based invalidation sufficient for now
    }
}