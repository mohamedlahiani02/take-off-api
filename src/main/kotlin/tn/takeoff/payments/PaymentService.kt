package tn.takeoff.payments

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.orders.OrderGateway
import tn.takeoff.orders.OrderStatus
import tn.takeoff.orders.PaymentMethod
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentService(
    private val intents: PaymentIntentGateway,
    @Value("\${KONNECT_API_KEY:}") private val konnectApiKey: String,
    @Value("\${KONNECT_WALLET_ID:}") private val konnectWalletId: String,
    @Value("\${KONNECT_WEBHOOK_SECRET:}") private val webhookSecret: String,
    private val orderGateway: OrderGateway,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * Reference types a payment intent may be raised against. Anything outside this set is
         * refused: an intent must always resolve to a real entity whose outstanding amount the
         * server can compute. Add a type here only together with its resolver branch in [initiate].
         */
        private val SUPPORTED_REF_TYPES = setOf("ORDER")

        /** Konnect operates in Tunisian dinars; amounts are sent in millimes. */
        const val CURRENCY = "TND"
    }

    data class InitiateResult(val intentId: UUID, val paymentUrl: String)

    @Transactional
    fun initiate(userId: UUID?, refType: String, refId: String, amountDt: BigDecimal, returnUrl: String): InitiateResult {
        // Every intent must name a supported, existing, owned, payable entity. The amount is
        // always resolved server-side from that entity; the client-supplied amount is advisory
        // only and is never trusted. Unknown reference types are refused outright rather than
        // falling through to a permissive "pay anything" path.
        val normalizedRefType = refType.trim().uppercase()
        if (normalizedRefType !in SUPPORTED_REF_TYPES)
            throw BadRequestException(
                "takeoff.payment.unsupported_ref_type",
                "Unsupported payment reference type: $refType",
            )

        val authorizedAmount: BigDecimal = when (normalizedRefType) {
            "ORDER" -> {
                val orderId = try { UUID.fromString(refId) } catch (_: Exception) {
                    throw BadRequestException("takeoff.payment.invalid_ref", "Invalid order reference")
                }
                val order = orderGateway.findById(orderId)
                    .orElseThrow { BadRequestException("takeoff.payment.invalid_ref", "Order not found") }
                if (userId == null || order.user?.id != userId)
                    throw BadRequestException("takeoff.payment.access_denied", "Access denied")
                if (order.status != OrderStatus.PENDING)
                    throw BadRequestException("takeoff.payment.order_not_payable", "Order is not in a payable state")
                if (order.paymentMethod != PaymentMethod.CARD)
                    throw BadRequestException(
                        "takeoff.payment.method_mismatch",
                        "Order is not awaiting a card payment",
                    )
                order.totalDt
            }
            else -> throw BadRequestException(
                "takeoff.payment.unsupported_ref_type",
                "Unsupported payment reference type: $refType",
            )
        }

        if (authorizedAmount <= BigDecimal.ZERO)
            throw BadRequestException("takeoff.payment.nothing_due", "Nothing is outstanding on this reference")

        if (konnectApiKey.isBlank()) {
            log.warn("KONNECT_API_KEY not set — stub payment mode")
            val intent = intents.save(PaymentIntent(
                userId = userId, refType = normalizedRefType, refId = refId, amountDt = authorizedAmount,
                konnectPayRef = "STUB-${UUID.randomUUID()}",
                konnectPayUrl = "$returnUrl?stub=true",
                status = "PENDING"
            ))
            return InitiateResult(intent.id, "$returnUrl?stub=true&intentId=${intent.id}")
        }

        val intentId = UUID.randomUUID()
        val restClient = RestClient.create()
        val body = mapOf(
            "receiverWalletId" to konnectWalletId,
            "token" to CURRENCY,
            "amount" to authorizedAmount.multiply(BigDecimal("1000")).toLong(),
            "type" to "immediate",
            "description" to "$normalizedRefType:$refId",
            "acceptedPaymentMethods" to listOf("wallet", "bank_card", "e-DINAR"),
            "successUrl" to "$returnUrl?intentId=$intentId",
            "failUrl" to "$returnUrl?intentId=$intentId",
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
            id = intentId,
            userId = userId, refType = normalizedRefType, refId = refId, amountDt = authorizedAmount,
            konnectPayRef = payRef, konnectPayUrl = payUrl, status = "PENDING"
        ))
        return InitiateResult(intent.id, payUrl)
    }

    @Transactional
    fun handleWebhook(rawBody: String, signature: String?): PaymentIntent {
        if (webhookSecret.isBlank()) throw SecurityException("Webhook secret not configured")
        if (signature == null) throw SecurityException("Missing webhook signature")
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(webhookSecret.toByteArray(), "HmacSHA256"))
        val computed = mac.doFinal(rawBody.toByteArray()).joinToString("") { "%02x".format(it) }
        if (computed != signature) throw SecurityException("Invalid webhook signature")

        @Suppress("UNCHECKED_CAST")
        val parsed = com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(rawBody, Map::class.java) as Map<String, Any?>
        val payRef = parsed["payment_ref"] as? String ?: parsed["paymentRef"] as? String
            ?: error("No payment_ref in webhook body")
        val statusRaw = ((parsed["status"] as? String) ?: "").uppercase()

        val intent = intents.findByKonnectPayRef(payRef)
            .orElseThrow { IllegalArgumentException("Unknown paymentRef: $payRef") }

        if (intent.status == "PAID") {
            log.info("Ignoring late callback for already-PAID intent ${intent.id}")
            return intent
        }

        intent.status = when {
            statusRaw == "PAID" || statusRaw == "COMPLETED" || statusRaw == "SUCCESS" -> "PAID"
            statusRaw.contains("FAIL") || statusRaw.contains("CANCEL") || statusRaw.contains("REJECT") -> "FAILED"
            else -> { log.warn("Unknown Konnect status: $statusRaw"); return intents.save(intent) }
        }
        intent.completedAt = java.time.Instant.now()
        val saved = intents.save(intent)
        if (saved.status == "PAID" && saved.refType.equals("ORDER", ignoreCase = true)) {
            try {
                val order = orderGateway.findById(UUID.fromString(saved.refId)).orElse(null)
                if (order != null && order.status == OrderStatus.PENDING) {
                    order.status = OrderStatus.CONFIRMED
                    order.updatedAt = java.time.Instant.now()
                    orderGateway.save(order)
                }
            } catch (_: Exception) {}
        }
        return saved
    }

    fun getStatus(intentId: UUID): PaymentIntent =
        intents.findById(intentId).orElseThrow { IllegalArgumentException("Intent not found: $intentId") }
}
