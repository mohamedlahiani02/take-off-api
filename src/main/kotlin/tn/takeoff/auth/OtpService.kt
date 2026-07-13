package tn.takeoff.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.common.errors.BadRequestException
import java.security.MessageDigest
import java.time.Instant
import java.util.logging.Logger

@Service
class OtpService(
    private val phoneOtpRepo: PhoneOtpGateway,
    @Value("\${takeoff.sms.enabled:false}") private val smsEnabled: Boolean,
    @Value("\${takeoff.sms.twilio-sid:}") private val twilioSid: String,
    @Value("\${takeoff.sms.twilio-token:}") private val twilioToken: String,
    @Value("\${takeoff.sms.from-number:}") private val fromNumber: String,
) {
    private val log = Logger.getLogger(OtpService::class.java.name)

    @Transactional
    fun sendOtp(phone: String) {
        val since = Instant.now().minusSeconds(600)
        val recentCount = phoneOtpRepo.countByPhoneAndCreatedAtAfter(phone, since)
        if (recentCount >= 3) {
            throw BadRequestException("takeoff.otp.rate_limit", "Too many OTP requests. Please wait 10 minutes.")
        }

        val code = (100000..999999).random().toString()
        val otp = PhoneOtp(
            phone = phone,
            codeHash = sha256(code),
            expiresAt = Instant.now().plusSeconds(600),
        )
        phoneOtpRepo.save(otp)

        if (smsEnabled && twilioSid.isNotBlank()) {
            sendViaTwilio(phone, code)
        } else {
            log.info("OTP for $phone: $code")
        }
    }

    fun verifyCode(phone: String, code: String): Boolean {
        val now = Instant.now()
        val otp = phoneOtpRepo.findTopByPhoneAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(phone, now)
            .orElse(null) ?: return false
        if (otp.codeHash != sha256(code)) return false
        otp.used = true
        phoneOtpRepo.save(otp)
        return true
    }

    private fun sendViaTwilio(to: String, code: String) {
        try {
            val url = "https://api.twilio.com/2010-04-01/Accounts/$twilioSid/Messages.json"
            val body = "To=${encode(to)}&From=${encode(fromNumber)}&Body=${encode("Your Take Off code: $code")}"
            val creds = java.util.Base64.getEncoder().encodeToString("$twilioSid:$twilioToken".toByteArray())
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Basic $creds")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.outputStream.write(body.toByteArray())
            val status = conn.responseCode
            if (status != 201) log.warning("Twilio returned $status for $to")
        } catch (e: Exception) {
            log.warning("Twilio send failed: ${e.message}")
        }
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
