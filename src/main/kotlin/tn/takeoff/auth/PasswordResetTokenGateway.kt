package tn.takeoff.auth

import java.util.Optional
import java.util.UUID

interface PasswordResetTokenGateway {
    fun findByTokenHash(hash: String): Optional<PasswordResetToken>
    fun save(token: PasswordResetToken): PasswordResetToken
    fun deleteAllByUserId(userId: UUID)
}
