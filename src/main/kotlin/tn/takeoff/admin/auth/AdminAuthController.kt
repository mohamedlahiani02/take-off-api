package tn.takeoff.admin.auth

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/auth")
class AdminAuthController(private val service: AdminAuthService) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: AdminLoginRequest): AdminLoginResponse =
        service.login(dto.email, dto.password)
}
