package tn.takeoff.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface PhoneOtpRepository : JpaRepository<PhoneOtp, UUID>, PhoneOtpGateway {
    override fun countByPhoneAndCreatedAtAfter(phone: String, since: Instant): Long
    override fun findTopByPhoneAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(phone: String, now: Instant): Optional<PhoneOtp>
    override fun deleteByPhoneAndUsedTrue(phone: String)
}
