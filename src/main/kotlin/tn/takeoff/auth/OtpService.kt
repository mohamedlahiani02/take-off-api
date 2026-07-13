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
    @Value("\${takeoff.sms.brevo-api-key:}") private val brevoApiKey: String,
    @Value("\${takeoff.sms.sender:TakeOff}") private val sender: String,
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

        if (smsEnabled && brevoApiKey.isNotBlank()) {
            sendViaBrevo(phone, code)
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

    private fun sendViaBrevo(to: String, code: String) {
        try {
            val body = """{"sender":"$sender","recipient":"$to","content":"Your Take Off verification code: $code. Valid for 10 minutes."}"""
            val url = java.net.URL("https://api.brevo.com/v3/transactionalSMS/sms")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("api-key", brevoApiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
            val status = conn.responseCode
            if (status !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                log.warning("Brevo SMS failed ($status): $err")
            }
        } catch (e: Exception) {
            log.warning("Brevo SMS error: ${e.message}")
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
