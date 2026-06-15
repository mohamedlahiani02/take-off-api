package tn.takeoff.admin

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import tn.takeoff.coaching.CoachingInquiry
import tn.takeoff.coaching.CoachingRepository
import tn.takeoff.orders.dto.OrderDto
import tn.takeoff.orders.OrderRepository
import tn.takeoff.orders.OrderService
import tn.takeoff.orders.dto.UpdateOrderStatusRequest
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val orderRepo: OrderRepository,
    private val orderService: OrderService,
    private val coachingRepo: CoachingRepository,
) {

    @GetMapping("/orders")
    fun orders(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<OrderDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return orderRepo.findAllByOrderByCreatedAtDesc(pageable).map { tn.takeoff.orders.dto.OrderDto.from(it) }
    }

    @PatchMapping("/orders/{id}/status")
    fun updateOrderStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody dto: UpdateOrderStatusRequest,
    ): OrderDto = orderService.updateStatus(id, dto.action)

    @GetMapping("/coaching")
    fun coaching(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<CoachingInquiry> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return coachingRepo.findAll(pageable)
    }
}
