package tn.takeoff.matches.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import tn.takeoff.matches.Match
import tn.takeoff.matches.MatchResult
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LogMatchRequest(
    val partnerName: String? = null,
    val opponentNames: List<String> = emptyList(),
    @field:NotNull val result: MatchResult,
    val score: String? = null,
    @field:DecimalMin("1.0") @field:DecimalMax("7.0") val opponentLevel: Double,
    @field:NotNull val playedAt: LocalDate,
)

data class MatchDto(
    val id: UUID,
    val partnerName: String?,
    val opponentNames: List<String>,
    val result: MatchResult,
    val score: String?,
    val opponentLevel: Double,
    val delta: Int,
    val pointsAfter: Int,
    val playedAt: LocalDate,
    val createdAt: Instant,
) {
    companion object {
        fun from(m: Match) = MatchDto(
            id = m.id,
            partnerName = m.partnerName,
            opponentNames = m.opponentNames.toList(),
            result = m.result,
            score = m.score,
            opponentLevel = m.opponentLevel,
            delta = m.delta,
            pointsAfter = m.pointsAfter,
            playedAt = m.playedAt,
            createdAt = m.createdAt,
        )
    }
}

data class LogMatchResponse(
    val match: MatchDto,
    val delta: Int,
    val newPoints: Int,
    val newLevel: Int,
)

data class LeaderboardEntry(
    val rank: Long,
    val userId: UUID,
    val name: String,
    val padelLevel: Int,
    val points: Int,
)
