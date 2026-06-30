package tn.takeoff.courts

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class CourtActivity { PADEL, PILATES }

@Entity
@Table(name = "courts")
class Court(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var activity: CourtActivity = CourtActivity.PADEL,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
