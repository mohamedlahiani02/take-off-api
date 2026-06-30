package tn.takeoff.cms

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "site_content")
class SiteContent(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var page: String,
    @Column(name = "section_key", nullable = false) var sectionKey: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    var content: Map<String, Any> = emptyMap(),
    @Column(nullable = false) var visible: Boolean = true,
    @Column(name = "display_order", nullable = false) var displayOrder: Int = 0,
    @Column(name = "updated_by_admin_id") var updatedByAdminId: UUID? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "global_settings")
class GlobalSetting(
    @Id @Column(name = "key") val key: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    var value: Map<String, Any> = emptyMap(),
    @Column(name = "updated_by_admin_id") var updatedByAdminId: UUID? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "media_assets")
class MediaAsset(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var url: String,
    @Column var filename: String? = null,
    @Column(name = "content_type") var contentType: String? = null,
    @Column(name = "size_bytes") var sizeBytes: Long? = null,
    @Column(name = "slot_id") var slotId: String? = null,
    @Column(name = "uploaded_by_admin_id") var uploadedByAdminId: UUID? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
)
