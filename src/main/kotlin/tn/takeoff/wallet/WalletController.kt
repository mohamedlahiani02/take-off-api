package tn.takeoff.wallet

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.users.UserRepository
import java.math.BigDecimal
import java.time.Instant

// TODO: implement proper wallet ledger; this is a mock top-up for MVP
@RestController
@RequestMapping("/api/v1/wallet")
class WalletController(private val userRepo: UserRepository) {

    data class TopUpRequest(val amountDt: BigDecimal)
    data class TopUpResponse(val newBalanceDt: BigDecimal, val message: String)

    @PostMapping("/topup")
    @ResponseStatus(HttpStatus.OK)
    fun topUp(
        @AuthenticationPrincipal claims: JwtService.Claims,
        @RequestBody dto: TopUpRequest,
    ): TopUpResponse {
        val user = userRepo.findById(claims.userId).orElseThrow()
        user.walletDt = user.walletDt.add(dto.amountDt)
        user.updatedAt = Instant.now()
        userRepo.save(user)
        return TopUpResponse(user.walletDt, "Top-up successful (mock)")
    }
}
