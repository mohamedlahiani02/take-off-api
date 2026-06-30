package tn.takeoff.cms

import jakarta.validation.constraints.NotBlank
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.auth.JwtService
import tn.takeoff.common.errors.NotFoundException
import java.time.Instant
import java.util.UUID

data class SectionRequest(
    @field:NotBlank val page: String,
    @field:NotBlank val sectionKey: String,
    val content: Map<String, Any> = emptyMap(),
    val visible: Boolean = true,
    val displayOrder: Int = 0,
)

data class SettingRequest(
    @field:NotBlank val key: String,
    val value: Map<String, Any> = emptyMap(),
)

data class MediaRequest(
    @field:NotBlank val url: String,
    val filename: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val slotId: String? = null,
)

@Service
class CmsService(
    private val content: SiteContentRepository,
    private val settings: GlobalSettingRepository,
    private val media: MediaAssetRepository,
    private val auditService: AuditService,
) {
    fun page(page: String): List<SiteContent> = content.findByPageOrderByDisplayOrder(page)
    fun allSettings(): List<GlobalSetting> = settings.findAll()
    fun listMedia(): List<MediaAsset> = media.findAllByOrderByCreatedAtDesc()

    /** Upsert a page section. */
    @Transactional
    fun saveSection(req: SectionRequest, adminId: UUID): SiteContent {
        val existing = content.findByPageAndSectionKey(req.page, req.sectionKey).orElse(null)
        val sc = existing ?: SiteContent(page = req.page, sectionKey = req.sectionKey)
        sc.content = req.content; sc.visible = req.visible; sc.displayOrder = req.displayOrder
        sc.updatedByAdminId = adminId; sc.updatedAt = Instant.now()
        content.save(sc)
        auditService.log(adminId, "cms.section_save", "site_content", "${req.page}/${req.sectionKey}")
        return sc
    }

    @Transactional
    fun saveSetting(req: SettingRequest, adminId: UUID): GlobalSetting {
        val existing = settings.findById(req.key).orElse(null)
        val s = existing ?: GlobalSetting(key = req.key)
        s.value = req.value; s.updatedByAdminId = adminId; s.updatedAt = Instant.now()
        settings.save(s)
        auditService.log(adminId, "cms.setting_save", "global_setting", req.key)
        return s
    }

    @Transactional
    fun addMedia(req: MediaRequest, adminId: UUID): MediaAsset {
        val m = MediaAsset(
            url = req.url, filename = req.filename, contentType = req.contentType,
            sizeBytes = req.sizeBytes, slotId = req.slotId, uploadedByAdminId = adminId,
        )
        media.save(m)
        auditService.log(adminId, "cms.media_add", "media_asset", m.id.toString())
        return m
    }

    @Transactional
    fun deleteMedia(id: UUID, adminId: UUID) {
        val m = media.findById(id).orElseThrow { NotFoundException("media_asset", id) }
        media.delete(m)
        auditService.log(adminId, "cms.media_delete", "media_asset", id.toString())
    }
}

/** Public read API — pages fetch their content/settings at load time. */
@RestController
@RequestMapping("/api/v1/content")
class PublicContentController(private val service: CmsService) {
    @GetMapping("/{page}") fun page(@PathVariable page: String): List<SiteContent> =
        service.page(page).filter { it.visible }
    @GetMapping("/settings/all") fun settings(): List<GlobalSetting> = service.allSettings()
}

@RestController
@RequestMapping("/api/v1/admin/content")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
class AdminContentController(private val service: CmsService) {

    @GetMapping("/{page}") fun page(@PathVariable page: String): List<SiteContent> = service.page(page)

    @PostMapping("/section")
    fun saveSection(@org.springframework.web.bind.annotation.RequestBody req: SectionRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): SiteContent =
        service.saveSection(req, a.adminId)

    @GetMapping("/settings") fun settings(): List<GlobalSetting> = service.allSettings()

    @PostMapping("/settings")
    fun saveSetting(@org.springframework.web.bind.annotation.RequestBody req: SettingRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): GlobalSetting =
        service.saveSetting(req, a.adminId)

    @GetMapping("/media") fun media(): List<MediaAsset> = service.listMedia()

    @PostMapping("/media")
    fun addMedia(@org.springframework.web.bind.annotation.RequestBody req: MediaRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): MediaAsset =
        service.addMedia(req, a.adminId)

    @DeleteMapping("/media/{id}")
    fun deleteMedia(@PathVariable id: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims) = service.deleteMedia(id, a.adminId)
}
