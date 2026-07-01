package tn.takeoff.wallet

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.users.WalletLedgerRepository
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/wallet")
class WalletController(
    private val ledgerRepo: WalletLedgerRepository,
) {
    data class TopUpRequest(val amountDt: BigDecimal)

    @PostMapping("/topup")
    fun topup(
        @RequestBody req: TopUpRequest,
        @AuthenticationPrincipal claims: JwtService.Claims,
    ): ResponseEntity<Any> {
        return ResponseEntity.status(405)
            .body(mapOf("error" to "Direct wallet top-up is disabled. Contact staff or use the payment flow."))
    }

    @GetMapping("/transactions")
    fun transactions(@AuthenticationPrincipal claims: JwtService.Claims) =
        ledgerRepo.findByUserIdOrderByCreatedAtDesc(claims.userId).map { l ->
            mapOf(
                "id" to l.id, "amountDt" to l.amountDt, "balanceAfter" to l.balanceAfter,
                "type" to l.type, "reason" to l.reason, "createdAt" to l.createdAt,
            )
        }
}