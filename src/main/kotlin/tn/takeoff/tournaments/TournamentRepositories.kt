package tn.takeoff.tournaments

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TournamentRepository : JpaRepository<Tournament, UUID> {
    fun findAllByOrderByStartsAtDesc(): List<Tournament>
}

interface TournamentFieldRepository : JpaRepository<TournamentField, UUID> {
    fun findByTournamentIdOrderByDisplayOrder(tournamentId: UUID): List<TournamentField>
    fun deleteByTournamentId(tournamentId: UUID)
}

interface TournamentRegistrationRepository : JpaRepository<TournamentRegistration, UUID> {
    fun findByTournamentIdOrderByCreatedAtDesc(tournamentId: UUID): List<TournamentRegistration>
    fun countByTournamentIdAndStatus(tournamentId: UUID, status: RegStatus): Long
}

interface TournamentPricingRepository : JpaRepository<TournamentPricing, UUID> {
    fun findByTournamentIdOrderByDisplayOrder(tournamentId: UUID): List<TournamentPricing>
    fun deleteByTournamentId(tournamentId: UUID)
}

interface TournamentPromoCodeRepository : JpaRepository<TournamentPromoCode, UUID> {
    fun findByTournamentId(tournamentId: UUID): List<TournamentPromoCode>
}
