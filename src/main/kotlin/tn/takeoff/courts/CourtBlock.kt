package tn.takeoff.courts

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "court_blocks")
class CourtBlock(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "court_id", nullable = false)
    var courtId: UUID,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    var endsAt: Instant,

    @Column(nullable = false)
    var reason: String,

    // NULL = one-off; otherwise weekly on this day-of-week (0=Sun..6=Sat)
    @Column(name = "recurring_dow")
    var recurringDow: Int? = null,

    @Column(name = "recurring_until")
    var recurringUntil: java.time.LocalDate? = null,

    @Column(name = "created_by_admin_id")
    var createdByAdminId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
