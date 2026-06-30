package tn.takeoff.admin.courts

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tn.takeoff.courts.BookingMode
import tn.takeoff.courts.BookingStatus
import tn.takeoff.courts.Court
import tn.takeoff.courts.CourtBlock
import tn.takeoff.courts.CourtBooking
import tn.takeoff.courts.CourtPaymentMethod
import tn.takeoff.courts.CourtPaymentStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CourtDto(
    val id: UUID,
    val name: String,
    val activity: String,
    val displayOrder: Int,
    val active: Boolean,
) {
    companion object {
        fun from(c: Court) = CourtDto(c.id, c.name, c.activity.name, c.displayOrder, c.active)
    }
}

data class BookingDto(
    val id: UUID,
    val courtId: UUID,
    val userId: UUID?,
    val startsAt: Instant,
    val endsAt: Instant,
    val mode: BookingMode,
    val priceDt: BigDecimal,
    val paymentStatus: CourtPaymentStatus,
    val paymentMethod: CourtPaymentMethod?,
    val status: BookingStatus,
    val cancelReason: String?,
) {
    companion object {
        fun from(b: CourtBooking) = BookingDto(
            id = b.id, courtId = b.courtId, userId = b.userId,
            startsAt = b.startsAt, endsAt = b.endsAt, mode = b.mode,
            priceDt = b.priceDt, paymentStatus = b.paymentStatus,
            paymentMethod = b.paymentMethod, status = b.status, cancelReason = b.cancelReason,
        )
    }
}

data class BlockDto(
    val id: UUID,
    val courtId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val reason: String,
    val recurringDow: Int?,
    val recurringUntil: LocalDate?,
) {
    companion object {
        fun from(b: CourtBlock) = BlockDto(
            id = b.id, courtId = b.courtId, startsAt = b.startsAt, endsAt = b.endsAt,
            reason = b.reason, recurringDow = b.recurringDow, recurringUntil = b.recurringUntil,
        )
    }
}

/** C-01: calendar window response. */
data class CalendarDto(
    val courts: List<CourtDto>,
    val bookings: List<BookingDto>,
    val blocks: List<BlockDto>,
)

/**
 * C-02/03/04: book on behalf. Provide an existing userId OR ghost details
 * (ghostName + ghostPhone) to create the user inline.
 */
data class CreateBookingRequest(
    @field:NotNull val courtId: UUID,
    @field:NotNull val startsAt: Instant,
    @field:NotNull val endsAt: Instant,
    @field:NotNull val mode: BookingMode,
    @field:NotNull val priceDt: BigDecimal,
    val userId: UUID? = null,
    val ghostName: String? = null,
    val ghostPhone: String? = null,
    val paymentStatus: CourtPaymentStatus = CourtPaymentStatus.PENDING,
    val paymentMethod: CourtPaymentMethod? = null,
)

/** C-05: cancel with optional wallet refund + mandatory reason. */
data class CancelBookingRequest(
    @field:NotBlank val reason: String,
    val refundToWallet: Boolean = false,
)

/** C-06: reschedule to a new court/time. */
data class RescheduleRequest(
    @field:NotNull val courtId: UUID,
    @field:NotNull val startsAt: Instant,
    @field:NotNull val endsAt: Instant,
)

/** C-07/08: create a block (recurringDow + recurringUntil = weekly recurrence). */
data class CreateBlockRequest(
    @field:NotNull val courtId: UUID,
    @field:NotNull val startsAt: Instant,
    @field:NotNull val endsAt: Instant,
    @field:NotBlank val reason: String,
    val recurringDow: Int? = null,
    val recurringUntil: LocalDate? = null,
)
