package tn.takeoff.payments

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentService(
    private val intents: PaymentIntentGateway,
    @Value("\${KONNECT_API_KEY:}") private val konnectApiKey: String,
    @Value("\${KONNECT_WALLET_ID:}") private val konnectWalletId: String,
    @Value("\${KONNECT_WEBHOOK_SECRET:}") private val webhookSecret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class InitiateResult(val intentId: UUID, val paymentUrl: String)

    @Transactional
    fun initiate(userId: UUID?, refType: String, refId: String, amountDt: BigDecimal, returnUrl: String): InitiateResult {
        if (konnectApiKey.isBlank()) {
            log.warn("KONNECT_API_KEY not set — stub payment mode")
            val intent = intents.save(PaymentIntent(
                userId = userId, refType = refType, refId = refId, amountDt = amountDt,
                konnectPayRef = "STUB-${UUID.randomUUID()}",
                konnectPayUrl = "$returnUrl?stub=true",
                status = "PENDING"
            ))
            return InitiateResult(intent.id, "$returnUrl?stub=true&intentId=${intent.id}")
        }

        val restClient = RestClient.create()
        val body = mapOf(
            "receiverWalletId" to konnectWalletId,
            "token" to "TND",
            "amount" to amountDt.multiply(BigDecimal("1000")).toLong(),
            "type" to "immediate",
            "description" to "$refType:$refId",
            "acceptedPaymentMethods" to listOf("wallet", "bank_card", "e-DINAR"),
            "successUrl" to returnUrl,
            "failUrl" to returnUrl,
        )

        @Suppress("UNCHECKED_CAST")
        val response = restClient.post()
            .uri("https://api.konnect.network/api/v2/payments/init-payment")
            .header("x-api-key", konnectApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map::class.java) as Map<String, Any?>

        val payRef = response["paymentRef"] as? String ?: error("No paymentRef in Konnect response")
        val payUrl = response["payUrl"] as? String ?: error("No payUrl in Konnect response")

        val intent = intents.save(PaymentIntent(
            userId = userId, refType = refType, refId = refId, amountDt = amountDt,
            konnectPayRef = payRef, konnectPayUrl = payUrl, status = "PENDING"
        ))
        return InitiateResult(intent.id, payUrl)
    }

    @Transactional
    fun handleWebhook(rawBody: String, signature: String?): PaymentIntent {
        if (webhookSecret.isNotBlank() && signature != null) {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(webhookSecret.toByteArray(), "HmacSHA256"))
            val computed = mac.doFinal(rawBody.toByteArray()).joinToString("") { "%02x".format(it) }
            if (computed != signature) throw SecurityException("Invalid webhook signature")
        }

        @Suppress("UNCHECKED_CAST")
        val parsed = com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(rawBody, Map::class.java) as Map<String, Any?>
        val payRef = parsed["payment_ref"] as? String ?: parsed["paymentRef"] as? String
            ?: error("No payment_ref in webhook body")
        val statusRaw = ((parsed["status"] as? String) ?: "").uppercase()

        val intent = intents.findByKonnectPayRef(payRef)
            .orElseThrow { IllegalArgumentException("Unknown paymentRef: $payRef") }

        intent.status = when {
            statusRaw.contains("PAID") || statusRaw.contains("COMPLETED") || statusRaw == "SUCCESS" -> "PAID"
            statusRaw.contains("FAIL") || statusRaw.contains("CANCEL") || statusRaw.contains("REJECT") -> "FAILED"
            else -> { log.warn("Unknown Konnect status: $statusRaw"); return intents.save(intent) }
        }
        intent.completedAt = java.time.Instant.now()
        return intents.save(intent)
    }

    fun getStatus(intentId: UUID): PaymentIntent =
        intents.findById(intentId).orElseThrow { IllegalArgumentException("Intent not found: $intentId") }
}
