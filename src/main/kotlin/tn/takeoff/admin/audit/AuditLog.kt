package tn.takeoff.admin.audit

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_log")
class AuditLog(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "admin_id", nullable = false)
    val adminId: UUID,

    @Column(nullable = false)
    val action: String,

    @Column(name = "target_type")
    val targetType: String? = null,

    @Column(name = "target_id")
    val targetId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val payload: Map<String, Any>? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
