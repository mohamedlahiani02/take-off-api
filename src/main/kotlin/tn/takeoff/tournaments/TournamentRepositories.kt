package tn.takeoff.tournaments

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface TournamentRepository : JpaRepository<Tournament, UUID> {
    fun findAllByOrderByStartsAtDesc(): List<Tournament>

    // Serialises concurrent registrations against the same tournament, so two
    // members racing for the last spot cannot both read "capacity available".
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tournament t where t.id = :id")
    fun findByIdForUpdate(id: UUID): Optional<Tournament>
}

interface TournamentFieldRepository : JpaRepository<TournamentField, UUID> {
    fun findByTournamentIdOrderByDisplayOrder(tournamentId: UUID): List<TournamentField>
    fun deleteByTournamentId(tournamentId: UUID)
}

interface TournamentRegistrationRepository : JpaRepository<TournamentRegistration, UUID> {
    fun findByTournamentIdOrderByCreatedAtDesc(tournamentId: UUID): List<TournamentRegistration>
    fun countByTournamentIdAndStatus(tournamentId: UUID, status: RegStatus): Long
    fun findByTournamentIdAndUserIdAndStatusIn(
        tournamentId: UUID, userId: UUID, statuses: List<RegStatus>,
    ): List<TournamentRegistration>
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<TournamentRegistration>
    fun findFirstByTournamentIdAndStatusOrderByCreatedAtAsc(
        tournamentId: UUID, status: RegStatus,
    ): TournamentRegistration?
}

interface TournamentPricingRepository : JpaRepository<TournamentPricing, UUID> {
    fun findByTournamentIdOrderByDisplayOrder(tournamentId: UUID): List<TournamentPricing>
    fun deleteByTournamentId(tournamentId: UUID)
}

interface TournamentPromoCodeRepository : JpaRepository<TournamentPromoCode, UUID> {
    fun findByTournamentId(tournamentId: UUID): List<TournamentPromoCode>
}
