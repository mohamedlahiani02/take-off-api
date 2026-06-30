package tn.takeoff.courts

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/courts")
class CourtsController(private val courts: CourtRepository) {

    data class PublicCourtDto(val id: UUID, val name: String, val activity: String)

    @GetMapping
    fun list(): List<PublicCourtDto> =
        courts.findByActiveOrderByDisplayOrder(true)
            .map { PublicCourtDto(it.id, it.name, it.activity.name) }
}
