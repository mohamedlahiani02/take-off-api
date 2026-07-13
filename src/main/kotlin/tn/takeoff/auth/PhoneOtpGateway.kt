package tn.takeoff.auth

import java.time.Instant
import java.util.Optional
import java.util.UUID

interface PhoneOtpGateway {
    fun save(otp: PhoneOtp): PhoneOtp
    fun countByPhoneAndCreatedAtAfter(phone: String, since: Instant): Long
    fun findTopByPhoneAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(phone: String, now: Instant): Optional<PhoneOtp>
    fun deleteByPhoneAndUsedTrue(phone: String)
    fun findById(id: UUID): Optional<PhoneOtp>
}
