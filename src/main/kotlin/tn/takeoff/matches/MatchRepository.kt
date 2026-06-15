package tn.takeoff.matches

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface MatchRepository : JpaRepository<Match, UUID> {

    fun findAllByUser_Id(userId: UUID, pageable: Pageable): Page<Match>

    @Query("""
        SELECT u.id, u.name, u.padel_level, u.points,
               RANK() OVER (ORDER BY u.points DESC) AS rank
        FROM users u
        WHERE u.points > 0
        ORDER BY u.points DESC
        LIMIT 50
    """, nativeQuery = true)
    fun leaderboard(): List<Array<Any>>
}
