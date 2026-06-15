package tn.takeoff.bookings

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// TODO: implement bookings (court slot booking + pack consumption)
@RestController
@RequestMapping("/api/v1/bookings")
class BookingsController {

    @GetMapping
    fun myBookings(): List<Any> = emptyList()
}
