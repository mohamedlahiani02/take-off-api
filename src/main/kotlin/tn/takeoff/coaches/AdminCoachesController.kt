package tn.takeoff.coaches

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import tn.takeoff.auth.JwtService
import tn.takeoff.coaches.dto.CoachDto
import tn.takeoff.coaches.dto.CreateCoachRequest
import tn.takeoff.coaches.dto.UpdateCoachRequest
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/coaches")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
class AdminCoachesController(private val service: CoachService) {

    @GetMapping
    fun list(): List<CoachDto> = service.listAll()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody dto: CreateCoachRequest,
        @AuthenticationPrincipal claims: JwtService.AdminClaims,
    ): CoachDto = service.create(dto, claims.adminId)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: UpdateCoachRequest,
        @AuthenticationPrincipal claims: JwtService.AdminClaims,
    ): CoachDto = service.update(id, dto, claims.adminId)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
        @AuthenticationPrincipal claims: JwtService.AdminClaims,
    ) = service.delete(id, claims.adminId)

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(
        @RequestBody ids: List<UUID>,
        @AuthenticationPrincipal claims: JwtService.AdminClaims,
    ) = service.reorder(ids, claims.adminId)

    @PostMapping("/{id}/photo")
    fun uploadPhoto(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal claims: JwtService.AdminClaims,
    ): CoachDto {
        val dir = System.getenv("COACH_PHOTOS_DIR") ?: "uploads/coaches"
        val uploadDir = Paths.get(dir)
        Files.createDirectories(uploadDir)
        val extension = file.originalFilename?.substringAfterLast('.', "jpg") ?: "jpg"
        val filename = "$id.$extension"
        file.transferTo(uploadDir.resolve(filename))
        val photoUrl = "/uploads/coaches/$filename"
        return service.updatePhoto(id, photoUrl, claims.adminId)
    }
}
