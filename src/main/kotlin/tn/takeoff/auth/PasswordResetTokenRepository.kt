package tn.takeoff.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID>, PasswordResetTokenGateway {
    override fun findByTokenHash(hash: String): Optional<PasswordResetToken>

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.id = :userId")
    override fun deleteAllByUserId(userId: UUID)
}
