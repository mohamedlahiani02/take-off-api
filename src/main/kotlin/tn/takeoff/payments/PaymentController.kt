package tn.takeoff.payments

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(private val paymentService: PaymentService) {

    data class InitiateRequest(val refType: String, val refId: String, val amountDt: BigDecimal, val returnUrl: String)
    data class InitiateResponse(val intentId: UUID, val paymentUrl: String)
    data class StatusResponse(val status: String, val refType: String, val refId: String, val amountDt: BigDecimal)

    @PostMapping("/initiate")
    fun initiate(
        @AuthenticationPrincipal claims: JwtService.Claims?,
        @RequestBody req: InitiateRequest,
    ): InitiateResponse {
        val result = paymentService.initiate(claims?.userId, req.refType, req.refId, req.amountDt, req.returnUrl)
        return InitiateResponse(result.intentId, result.paymentUrl)
    }

    @PostMapping("/webhook")
    fun webhook(
        @RequestBody rawBody: String,
        @RequestHeader(value = "x-konnect-signature", required = false) signature: String?,
    ): ResponseEntity<Void> {
        paymentService.handleWebhook(rawBody, signature)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/{id}/status")
    fun status(
        @AuthenticationPrincipal claims: JwtService.Claims?,
        @PathVariable id: UUID,
    ): StatusResponse {
        val intent = paymentService.getStatus(id)
        return StatusResponse(intent.status, intent.refType, intent.refId, intent.amountDt)
    }
}
