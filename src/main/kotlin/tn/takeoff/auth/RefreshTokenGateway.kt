package tn.takeoff.auth

import java.util.Optional
import java.util.UUID

interface RefreshTokenGateway {
    fun findByTokenHash(hash: String): Optional<RefreshToken>
    fun save(token: RefreshToken): RefreshToken
    fun delete(token: RefreshToken)
    fun deleteAllByUserId(userId: UUID)
}
