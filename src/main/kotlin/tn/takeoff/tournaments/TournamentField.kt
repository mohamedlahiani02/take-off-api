package tn.takeoff.tournaments

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class FieldType {
    SHORT_TEXT, LONG_TEXT, DROPDOWN, MULTI_SELECT, DATE, PARTNER,
    FILE, TSHIRT_SIZE, PHONE, AGREEMENT
}

@Entity
@Table(name = "tournament_fields")
class TournamentField(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tournament_id", nullable = false)
    var tournamentId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    var fieldType: FieldType,

    @Column(nullable = false)
    var label: String,

    @Column(name = "help_text")
    var helpText: String? = null,

    @Column(nullable = false)
    var required: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var options: Map<String, Any>? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
