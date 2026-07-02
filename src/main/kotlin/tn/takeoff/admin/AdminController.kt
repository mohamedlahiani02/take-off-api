package tn.takeoff.admin

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tn.takeoff.coaching.CoachingGateway
import tn.takeoff.coaching.CoachingInquiry
import tn.takeoff.orders.OrderGateway
import tn.takeoff.orders.OrderStatus
import tn.takeoff.orders.dto.OrderDto
import tn.takeoff.orders.OrderService
import tn.takeoff.orders.dto.UpdateOrderStatusRequest
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.users.UserGateway
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import java.math.BigDecimal
import java.util.UUID

data class AdminTopupRequest(val amountDt: BigDecimal, val reason: String = "admin_topup")

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','RECEPTION','COACH')")
class AdminController(
    private val orderRepo: OrderGateway,
    private val orderService: OrderService,
    private val coachingRepo: CoachingGateway,
    private val userGateway: UserGateway,
    private val walletService: WalletService,
) {

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    fun orders(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) status: String?,
    ): Page<OrderDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val statusEnum = status?.let { runCatching { OrderStatus.valueOf(it.uppercase()) }.getOrNull() }
        return if (statusEnum != null)
            orderRepo.findByStatusOrderByCreatedAtDesc(statusEnum, pageable).map { OrderDto.from(it) }
        else
            orderRepo.findAllByOrderByCreatedAtDesc(pageable).map { OrderDto.from(it) }
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    fun orderDetail(@PathVariable id: UUID): OrderDto {
        val order = orderRepo.findById(id).orElseThrow { NotFoundException("order", id) }
        return OrderDto.from(order)
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

    @PostMapping("/wallet/topup/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','RECEPTION')")
    fun adminWalletTopup(
        @PathVariable userId: UUID,
        @RequestBody req: AdminTopupRequest,
    ): Map<String, Any> {
        val newBalance = walletService.apply(
            userId = userId,
            delta = req.amountDt,
            type = WalletEntryType.ADMIN_CREDIT,
            reason = req.reason,
        )
        return mapOf("userId" to userId, "newBalanceDt" to newBalance, "reason" to req.reason)
    }
}