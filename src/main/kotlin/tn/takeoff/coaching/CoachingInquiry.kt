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
)
