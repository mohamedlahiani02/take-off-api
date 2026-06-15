package tn.takeoff.matches

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.matches.dto.*

@RestController
@RequestMapping("/api/v1")
class MatchController(private val service: MatchService) {

    @GetMapping("/matches")
    fun list(
        @AuthenticationPrincipal claims: JwtService.Claims,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<MatchDto> = service.list(claims.userId, page, size)

    @PostMapping("/matches")
    @ResponseStatus(HttpStatus.CREATED)
    fun log(
        @AuthenticationPrincipal claims: JwtService.Claims,
        @Valid @RequestBody dto: LogMatchRequest,
    ): LogMatchResponse = service.log(claims.userId, dto)

    @GetMapping("/leaderboard")
    fun leaderboard(): List<LeaderboardEntry> = service.leaderboard()
}
