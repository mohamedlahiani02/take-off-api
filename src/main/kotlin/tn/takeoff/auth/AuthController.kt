package tn.takeoff.auth

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.dto.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody dto: RegisterRequest): AuthResponse =
        authService.register(dto)

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginRequest): AuthResponse =
        authService.login(dto)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody dto: RefreshRequest): TokenPair =
        authService.refresh(dto)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal claims: JwtService.Claims) =
        authService.logout(claims.userId)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal claims: JwtService.Claims): UserDto =
        authService.me(claims.userId)

    @PatchMapping("/me")
    fun updateMe(
        @AuthenticationPrincipal claims: JwtService.Claims,
        @RequestBody dto: UpdateMeRequest,
    ): UserDto = authService.updateMe(claims.userId, dto)
}
