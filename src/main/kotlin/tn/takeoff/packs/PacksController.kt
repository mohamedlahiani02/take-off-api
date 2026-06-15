package tn.takeoff.packs

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// TODO: implement packs (purchase, list, consume on booking)
@RestController
@RequestMapping("/api/v1/packs")
class PacksController {

    @GetMapping
    fun myPacks(): List<Any> = emptyList()
}
