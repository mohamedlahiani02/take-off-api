package tn.takeoff.cms

import com.cloudinary.Cloudinary
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import tn.takeoff.auth.JwtService
import tn.takeoff.common.errors.BadRequestException
import java.util.UUID

/**
 * Uploads images to Cloudinary and records them in media_assets.
 * Configured from the CLOUDINARY_URL env var (cloudinary://key:secret@cloud_name).
 */
@Service
class MediaUploadService(private val media: MediaAssetRepository) {

    private val cloudinary: Cloudinary? by lazy {
        val url = System.getenv("CLOUDINARY_URL")?.trim()
        if (url.isNullOrBlank()) null else Cloudinary(url)
    }

    fun upload(file: MultipartFile, folder: String?, adminId: UUID): MediaAsset {
        val cl = cloudinary ?: throw BadRequestException(
            "takeoff.media.not_configured",
            "Image uploads are not configured. Set CLOUDINARY_URL in the server environment.",
        )
        if (file.isEmpty) throw BadRequestException("takeoff.media.empty", "No file provided")

        @Suppress("UNCHECKED_CAST")
        val options = mutableMapOf<String, Any>("resource_type" to "image")
        if (!folder.isNullOrBlank()) options["folder"] = "takeoff/$folder"

        val result = cl.uploader().upload(file.bytes, options)
        val url = result["secure_url"]?.toString()
            ?: throw BadRequestException("takeoff.media.upload_failed", "Upload failed")

        val asset = MediaAsset(
            url = url,
            filename = file.originalFilename,
            contentType = file.contentType,
            sizeBytes = file.size,
            slotId = folder,
            uploadedByAdminId = adminId,
        )
        media.save(asset)
        return asset
    }
}

@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','RECEPTION','COACH')")
class MediaUploadController(private val service: MediaUploadService) {

    /** Multipart upload → returns the persisted, CDN-served asset. */
    @PostMapping("/upload")
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) folder: String?,
        @AuthenticationPrincipal admin: JwtService.AdminClaims,
    ): MediaAsset = service.upload(file, folder, admin.adminId)
}
