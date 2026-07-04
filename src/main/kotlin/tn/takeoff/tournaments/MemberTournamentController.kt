package tn.takeoff.tournaments

import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PublicTournamentDto(
    val id: UUID,
    val title: String,
    val description: String?,
    val bannerUrl: String?,
    val format: TournamentFormat,
    val category: TournamentCategory,
    val startsAt: Instant,
    val endsAt: Instant?,
    val entryFeeDt: BigDecimal,
    val prize: String?,
    val status: TournamentStatus,
    val maxParticipants: Int?,
    val registrationDeadline: Instant?,
    val currentRegistrations: Long,
)

@RestController
@RequestMapping("/api/v1/tournaments")
class MemberTournamentController(
    private val tournaments: TournamentRepository,
    private val registrations: TournamentRegistrationRepository,
) {
    @GetMapping
    fun listPublic(): List<PublicTournamentDto> =
        tournaments.findAllByOrderByStartsAtDesc()
            .filter { it.status != TournamentStatus.DRAFT }
            .map { t ->
                PublicTournamentDto(
                    id = t.id,
                    title = t.title,
                    description = t.description,
                    bannerUrl = t.bannerUrl,
                    format = t.format,
                    category = t.category,
                    startsAt = t.startsAt,
                    endsAt = t.endsAt,
                    entryFeeDt = t.entryFeeDt,
                    prize = t.prize,
                    status = t.status,
                    maxParticipants = t.maxParticipants,
                    registrationDeadline = t.registrationDeadline,
                    currentRegistrations = registrations.countByTournamentIdAndStatus(t.id, RegStatus.CONFIRMED),
                )
            }
}
