package tn.takeoff.coaches

import org.springframework.web.bind.annotation.*
import tn.takeoff.coaches.dto.CoachDto

@RestController
@RequestMapping("/api/v1/coaches")
class CoachesController(private val service: CoachService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "PADEL") activity: String,
        @RequestParam(defaultValue = "false") preview: Boolean,
    ): List<CoachDto> = service.listPublic(CoachActivity.valueOf(activity.uppercase()), preview)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: java.util.UUID): CoachDto = service.getPublic(id)
}
