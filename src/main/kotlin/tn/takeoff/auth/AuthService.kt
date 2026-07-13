package tn.takeoff.auth

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.auth.dto.*
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.ConflictException
import tn.takeoff.common.errors.UnauthorizedException
import tn.takeoff.users.AccountStatus
import tn.takeoff.users.User
import tn.takeoff.users.UserGateway
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepo: UserGateway,
    private val refreshTokenRepo: RefreshTokenGateway,
    private val passwordResetTokenRepo: PasswordResetTokenGateway,
    private val otpService: OtpService,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun register(dto: RegisterRequest): AuthResponse {
        val email = dto.email.trim().lowercase()
        val phone = normalizePhone(dto.phone)
        if (userRepo.existsByEmail(email)) {
            throw ConflictException("takeoff.auth.email_taken", "Email already in use")
        }
        if (userRepo.existsByPhone(phone)) {
            throw ConflictException("takeoff.auth.phone_taken", "Phone number already in use")
        }
        val user = User(
            email = email,
            passwordHash = passwordEncoder.encode(dto.password),
            name = dto.name,
            phone = phone,
            tracks = dto.tracks.toTypedArray(),
        )
        userRepo.save(user)
        return issueAuth(user)
    }

    @Transactional
    fun login(dto: LoginRequest): AuthResponse {
        val user = userRepo.findByPhone(normalizePhone(dto.phone))
            .orElseThrow { UnauthorizedException("takeoff.auth.invalid_credentials", "Invalid credentials") }
        if (user.accountStatus == AccountStatus.BLOCKED) {
            throw UnauthorizedException("takeoff.auth.account_blocked", "Account is blocked")
        }
        if (user.accountStatus != AccountStatus.ACTIVE) {
            throw UnauthorizedException("takeoff.auth.invalid_credentials", "Invalid credentials")
        }
        if (user.passwordHash == null || !passwordEncoder.matches(dto.password, user.passwordHash)) {
            throw UnauthorizedException("takeoff.auth.invalid_credentials", "Invalid credentials")
        }
        return issueAuth(user)
    }

    @Transactional
    fun sendOtp(dto: SendOtpRequest): SendOtpResponse {
        val phone = normalizePhone(dto.phone)
        otpService.sendOtp(phone)
        val isNewUser = !userRepo.existsByPhone(phone)
        return SendOtpResponse(
            message = if (isNewUser) "Code sent. Welcome to Take Off!" else "Code sent.",
            isNewUser = isNewUser,
        )
    }

    @Transactional
    fun verifyOtp(dto: VerifyOtpRequest): AuthResponse {
        val phone = normalizePhone(dto.phone)
        val valid = otpService.verifyCode(phone, dto.code)
        if (!valid) {
            throw BadRequestException("takeoff.otp.invalid", "Invalid or expired code")
        }

        val existing = userRepo.findByPhone(phone).orElse(null)
        val user = if (existing != null) {
            if (existing.accountStatus == AccountStatus.BLOCKED) {
                throw UnauthorizedException("takeoff.auth.account_blocked", "Account is blocked")
            }
            existing
        } else {
            val name = dto.name?.trim()?.takeIf { it.isNotBlank() }
                ?: throw BadRequestException("takeoff.auth.name_required", "Name is required for new accounts")
            val newUser = User(
                email = null,
                passwordHash = null,
                name = name,
                phone = phone,
            )
            userRepo.save(newUser)
        }

        return issueAuth(user)
    }

    private fun normalizePhone(input: String): String {
        var digits = input.trim().replace(Regex("[\\s-]"), "")
        digits = digits.removePrefix("+")
        if (digits.startsWith("00216")) digits = digits.removePrefix("00216")
        else if (digits.startsWith("216")) digits = digits.removePrefix("216")
        return if (digits.length == 8 && digits.all { it.isDigit() }) "+216$digits" else input.trim()
    }

    @Transactional
    fun refresh(dto: RefreshRequest): TokenPair {
        val hash = sha256(dto.refreshToken)
        val stored = refreshTokenRepo.findByTokenHash(hash)
            .orElseThrow { UnauthorizedException("takeoff.auth.invalid_refresh", "Refresh token invalid or expired") }

        if (stored.expiresAt.isBefore(Instant.now())) {
            refreshTokenRepo.delete(stored)
            throw UnauthorizedException("takeoff.auth.refresh_expired", "Refresh token expired")
        }

        refreshTokenRepo.delete(stored)
        val claims = JwtService.Claims(stored.user.id, stored.user.email, stored.user.name, stored.user.role)
        val newAccess = jwtService.issueAccessToken(claims)
        val (newRefreshRaw, newRefreshEntity) = buildRefreshToken(stored.user)
        refreshTokenRepo.save(newRefreshEntity)
        return TokenPair(newAccess, newRefreshRaw)
    }

    @Transactional
    fun logout(userId: UUID) {
        refreshTokenRepo.deleteAllByUserId(userId)
    }

    fun me(userId: UUID): UserDto {
        val user = userRepo.findById(userId)
            .orElseThrow { UnauthorizedException() }
        return UserDto.from(user)
    }

    @Transactional
    fun updateMe(userId: UUID, dto: UpdateMeRequest): UserDto {
        val user = userRepo.findById(userId)
            .orElseThrow { UnauthorizedException() }
        dto.name?.let { user.name = it }
        dto.phone?.let { user.phone = it }
        dto.tracks?.let { user.tracks = it.toTypedArray() }
        if (dto.currentPassword != null && dto.newPassword != null) {
            if (user.passwordHash == null || !passwordEncoder.matches(dto.currentPassword, user.passwordHash))
                throw UnauthorizedException("takeoff.auth.wrong_password", "Current password is incorrect")
            if (dto.newPassword.length < 8)
                throw BadRequestException("takeoff.auth.weak_password", "Password must be at least 8 characters")
            user.passwordHash = passwordEncoder.encode(dto.newPassword)
        }
        user.updatedAt = Instant.now()
        return UserDto.from(userRepo.save(user))
    }

    @Transactional
    fun forgotPassword(dto: ForgotPasswordRequest) {
        val user = userRepo.findByEmail(dto.email.trim().lowercase()).orElse(null) ?: return
        passwordResetTokenRepo.deleteAllByUserId(user.id)
        val raw = UUID.randomUUID().toString()
        passwordResetTokenRepo.save(
            PasswordResetToken(user = user, tokenHash = sha256(raw), expiresAt = Instant.now().plusSeconds(3600))
        )
    }

    @Transactional
    fun resetPassword(dto: ResetPasswordRequest) {
        val stored = passwordResetTokenRepo.findByTokenHash(sha256(dto.token))
            .orElseThrow { UnauthorizedException("takeoff.auth.invalid_reset", "Token invalid or expired") }
        if (stored.used || stored.expiresAt.isBefore(Instant.now()))
            throw UnauthorizedException("takeoff.auth.invalid_reset", "Token invalid or expired")
        stored.user.passwordHash = passwordEncoder.encode(dto.newPassword)
        stored.user.updatedAt = Instant.now()
        userRepo.save(stored.user)
        stored.used = true
        passwordResetTokenRepo.save(stored)
    }

    private fun issueAuth(user: User): AuthResponse {
        val claims = JwtService.Claims(user.id, user.email, user.name, user.role)
        val access = jwtService.issueAccessToken(claims)
        val (refreshRaw, refreshEntity) = buildRefreshToken(user)
        refreshTokenRepo.save(refreshEntity)
        return AuthResponse(TokenPair(access, refreshRaw), UserDto.from(user))
    }

    private fun buildRefreshToken(user: User): Pair<String, RefreshToken> {
        val raw = jwtService.issueRefreshToken()
        val entity = RefreshToken(
            user = user,
            tokenHash = sha256(raw),
            expiresAt = Instant.now().plusSeconds(604800L * 4),
        )
        return raw to entity
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
