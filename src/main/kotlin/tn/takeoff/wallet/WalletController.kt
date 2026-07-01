package tn.takeoff.wallet

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletLedgerRepository
import tn.takeoff.users.WalletService
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/wallet")
class WalletController(
    private val walletService: WalletService,
    private val ledgerRepo: WalletLedgerRepository,
) {
    data class TopUpRequest(val amountDt: BigDecimal)
    data class TopUpResponse(val newBalanceDt: BigDecimal, val message: String)

    @PostMapping("/topup")
    @ResponseStatus(HttpStatus.OK)
    fun topUp(
        @AuthenticationPrincipal claims: JwtService.Claims,
        @RequestBody dto: TopUpRequest,
    ): TopUpResponse {
        if (dto.amountDt <= BigDecimal.ZERO)
            throw IllegalArgumentException("Amount must be positive")
        val newBalance = walletService.apply(
            userId = claims.userId,
            delta = dto.amountDt,
            type = WalletEntryType.TOPUP,
            reason = "member_topup",
        )
        return TopUpResponse(newBalance, "Top-up successful")
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
