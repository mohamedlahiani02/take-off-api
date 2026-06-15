package tn.takeoff.orders

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import tn.takeoff.auth.JwtService
import tn.takeoff.orders.dto.*

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val service: OrderService) {

    @GetMapping
    fun myOrders(
        @AuthenticationPrincipal claims: JwtService.Claims,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<OrderDto> = service.myOrders(claims.userId, page, size)

    @GetMapping("/{id}")
    fun getById(
        @AuthenticationPrincipal claims: JwtService.Claims,
        @PathVariable id: java.util.UUID,
    ): OrderDto = service.getById(id, claims.userId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun place(
        @AuthenticationPrincipal claims: JwtService.Claims?,
        @Valid @RequestBody dto: PlaceOrderRequest,
    ): OrderDto = service.place(claims?.userId, dto)
}
