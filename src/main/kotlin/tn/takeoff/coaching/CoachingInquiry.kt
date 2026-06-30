package tn.takeoff.coaching

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coaching_inquiries")
class CoachingInquiry(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val phone: String,

    @Column(nullable = false)
    val email: String,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "lesson_types", columnDefinition = "text[]")
    val lessonTypes: Array<String> = arrayOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "availability_grid", columnDefinition = "jsonb")
    val availabilityGrid: Map<String, Boolean> = emptyMap(),

    val level: String? = null,

    @Column(columnDefinition = "text")
    val notes: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    // ── Epic H3 pipeline ──
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: InquiryStatus = InquiryStatus.NEW,

    @Column(name = "assigned_coach_id")
    var assignedCoachId: UUID? = null,

    @Column(name = "admin_note", columnDefinition = "text")
    var adminNote: String? = null,

    @Enumerated(EnumType.STRING)
    @Column
    var outcome: InquiryOutcome? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

enum class InquiryStatus { NEW, CONTACTED, SCHEDULED, CLOSED }
enum class InquiryOutcome { CONVERTED, NO_SHOW, NOT_INTERESTED }
