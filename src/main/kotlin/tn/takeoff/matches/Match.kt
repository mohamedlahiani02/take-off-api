package tn.takeoff.matches

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import tn.takeoff.users.User
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class MatchResult { W, L }

@Entity
@Table(name = "matches")
class Match(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "partner_name")
    var partnerName: String? = null,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "opponent_names", columnDefinition = "text[]")
    var opponentNames: Array<String> = arrayOf(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var result: MatchResult,

    var score: String? = null,

    @Column(name = "opponent_level", columnDefinition = "numeric")
    var opponentLevel: Double,

    var delta: Int = 0,

    @Column(name = "points_after")
    var pointsAfter: Int = 0,

    @Column(name = "played_at")
    var playedAt: LocalDate,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
