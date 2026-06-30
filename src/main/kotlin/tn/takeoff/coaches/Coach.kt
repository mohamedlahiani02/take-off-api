package tn.takeoff.coaches

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coaches")
class Coach(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "first_name", nullable = false)
    var firstName: String,

    @Column(name = "last_name", nullable = false)
    var lastName: String,

    @Column(name = "role_title", nullable = false)
    var roleTitle: String,

    @Column(nullable = false, columnDefinition = "text")
    var bio: String,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var specs: Array<String> = arrayOf(),

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var achievements: Array<String> = arrayOf(),

    @Column(name = "photo_url")
    var photoUrl: String? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "show_on_padel_preview", nullable = false)
    var showOnPadelPreview: Boolean = false,

    @Column(nullable = false)
    var active: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var activity: CoachActivity = CoachActivity.PADEL,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
