package tn.takeoff.matches

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.common.errors.UnauthorizedException
import tn.takeoff.matches.dto.*
import tn.takeoff.users.UserRepository
import java.time.Instant
import java.util.UUID

@Service
class MatchService(
    private val matchRepo: MatchRepository,
    private val userRepo: UserRepository,
    private val eloService: EloService,
) {

    fun list(userId: UUID, page: Int, size: Int): Page<MatchDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "playedAt"))
        return matchRepo.findAllByUser_Id(userId, pageable).map { MatchDto.from(it) }
    }

    @Transactional
    fun log(userId: UUID, dto: LogMatchRequest): LogMatchResponse {
        val user = userRepo.findById(userId).orElseThrow { UnauthorizedException() }
        val delta = eloService.computeDelta(user.padelLevel, dto.opponentLevel, dto.result.name)
        val newPoints = (user.points + delta).coerceAtLeast(0)
        val newLevel = eloService.pointsToLevel(newPoints)

        user.points = newPoints
        user.padelLevel = newLevel
        user.updatedAt = Instant.now()
        userRepo.save(user)

        val match = Match(
            user = user,
            partnerName = dto.partnerName,
            opponentNames = dto.opponentNames.toTypedArray(),
            result = dto.result,
            score = dto.score,
            opponentLevel = dto.opponentLevel,
            delta = delta,
            pointsAfter = newPoints,
            playedAt = dto.playedAt,
        )
        matchRepo.save(match)

        return LogMatchResponse(MatchDto.from(match), delta, newPoints, newLevel)
    }

    fun leaderboard(): List<LeaderboardEntry> =
        matchRepo.leaderboard().map { row ->
            LeaderboardEntry(
                rank = (row[4] as Number).toLong(),
                userId = UUID.fromString(row[0].toString()),
                name = row[1].toString(),
                padelLevel = (row[2] as Number).toInt(),
                points = (row[3] as Number).toInt(),
            )
        }
}
