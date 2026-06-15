package tn.takeoff.courts

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// TODO: implement courts + slots availability
@RestController
@RequestMapping("/api/v1/courts")
class CourtsController {

    @GetMapping
    fun list(): List<Map<String, String>> = listOf(
        mapOf("id" to "court-1", "name" to "Court 1", "surface" to "artificial_grass"),
        mapOf("id" to "court-2", "name" to "Court 2", "surface" to "artificial_grass"),
    )
}
