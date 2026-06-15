package tn.takeoff.auth

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.auth.dto.*
import tn.takeoff.common.errors.ConflictException
import tn.takeoff.common.errors.UnauthorizedException
import tn.takeoff.users.User
import tn.takeoff.users.UserRepository
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepo: UserRepository,
    private val refreshTokenRepo: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun register(dto: RegisterRequest): AuthResponse {
        if (userRepo.existsByEmail(dto.email)) {
            throw ConflictException("takeoff.auth.email_taken", "Email already in use")
        }
        val user = User(
            email = dto.email,
            passwordHash = passwordEncoder.encode(dto.password),
            name = dto.name,
            phone = dto.phone,
            tracks = dto.tracks.toTypedArray(),
        )
        userRepo.save(user)
        return issueAuth(user)
    }

    @Transactional
    fun login(dto: LoginRequest): AuthResponse {
        val user = userRepo.findByEmail(dto.email)
            .orElseThrow { UnauthorizedException("takeoff.auth.invalid_credentials", "Invalid email or password") }
        if (!passwordEncoder.matches(dto.password, user.passwordHash)) {
            throw UnauthorizedException("takeoff.auth.invalid_credentials", "Invalid email or password")
        }
        return issueAuth(user)
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

        // rotate: delete old, issue new
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
        user.updatedAt = Instant.now()
        return UserDto.from(userRepo.save(user))
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
            expiresAt = Instant.now().plusSeconds(604800L * 4), // 28 days
        )
        return raw to entity
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
