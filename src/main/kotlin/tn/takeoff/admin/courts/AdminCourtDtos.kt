package tn.takeoff.admin.courts

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tn.takeoff.courts.BookingMode
import tn.takeoff.courts.BookingStatus
import tn.takeoff.courts.Court
import tn.takeoff.courts.CourtBlock
import tn.takeoff.courts.CourtBooking
import tn.takeoff.courts.CourtBookingPlayer
import tn.takeoff.courts.CourtPaymentMethod
import tn.takeoff.courts.CourtPaymentStatus
import tn.takeoff.courts.PlayerPaymentMethod
import tn.takeoff.courts.PlayerPaymentStatus
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

/** Derived booking-level payment state — drives calendar colors (US-1.4). */
enum class BookingPaymentState { UNPAID, PARTIAL, PAID }

data class ParticipantDto(
    val id: UUID,
    val userId: UUID,
    val userName: String?,
    val shareDt: BigDecimal,
    val paymentStatus: PlayerPaymentStatus,
    val paymentMethod: PlayerPaymentMethod?,
    val paidAt: Instant?,
    val noShow: Boolean,
) {
    companion object {
        fun from(p: CourtBookingPlayer, userName: String? = null) = ParticipantDto(
            id = p.id, userId = p.userId, userName = userName, shareDt = p.shareDt,
            paymentStatus = p.paymentStatus, paymentMethod = p.paymentMethod,
            paidAt = p.paidAt, noShow = p.noShow,
        )
    }
}

data class BookingDto(
    val id: UUID,
    val courtId: UUID,
    val userId: UUID?,
    val userName: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val mode: BookingMode,
    val priceDt: BigDecimal,
    val paymentStatus: CourtPaymentStatus,
    val paymentMethod: CourtPaymentMethod?,
    val status: BookingStatus,
    val cancelReason: String?,
    val participants: List<ParticipantDto> = emptyList(),
    val paymentState: BookingPaymentState = BookingPaymentState.UNPAID,
    val paidCount: Int = 0,
    val totalSlots: Int = 1,
) {
    companion object {
        // A padel SHARE booking is a 4-player match; FULL (or non-padel) is a single payer.
        const val SHARE_SLOTS = 4

        fun from(
            b: CourtBooking,
            userName: String? = null,
            players: List<CourtBookingPlayer> = emptyList(),
            playerNames: Map<UUID, String> = emptyMap(),
        ): BookingDto {
            val slots = if (b.mode == BookingMode.SHARE) SHARE_SLOTS else 1
            val settled = players.count { it.paymentStatus != PlayerPaymentStatus.PENDING }
            val state = when {
                b.paymentStatus == CourtPaymentStatus.PAID -> BookingPaymentState.PAID
                settled == 0 -> BookingPaymentState.UNPAID
                settled >= slots -> BookingPaymentState.PAID
                else -> BookingPaymentState.PARTIAL
            }
            return BookingDto(
                id = b.id, courtId = b.courtId, userId = b.userId, userName = userName,
                startsAt = b.startsAt, endsAt = b.endsAt, mode = b.mode,
                priceDt = b.priceDt, paymentStatus = b.paymentStatus,
                paymentMethod = b.paymentMethod, status = b.status, cancelReason = b.cancelReason,
                participants = players.map { ParticipantDto.from(it, playerNames[it.userId]) },
                paymentState = state,
                paidCount = settled,
                totalSlots = slots,
            )
        }
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

/** C-09: update payment status on an existing booking. */
data class UpdatePaymentStatusRequest(val paymentStatus: CourtPaymentStatus)

/**
 * P-01: attach a participant to a booking slot (US-1.3). Existing member via userId,
 * or create a new member inline (newMemberName + newMemberPhone — a real user record,
 * claimable later; never a throwaway guest). shareDt defaults to price/4 for SHARE.
 */
data class AddParticipantRequest(
    val userId: UUID? = null,
    val newMemberName: String? = null,
    val newMemberPhone: String? = null,
    val shareDt: BigDecimal? = null,
)

/** P-02: settle / adjust one tranche (US-1.2, US-3.5). */
data class UpdateParticipantRequest(
    val paymentStatus: PlayerPaymentStatus? = null,
    val paymentMethod: PlayerPaymentMethod? = null,
    val noShow: Boolean? = null,
)

/** US-3.4: one member's outstanding court debt. */
data class ReceivableDto(
    val userId: UUID,
    val userName: String?,
    val phone: String?,
    val totalDueDt: BigDecimal,
    val unpaidCount: Int,
    val oldestDue: Instant?,
    val noShowCount: Int,
)

/** History entry for a court booking, returned in user profile. */
data class CourtBookingHistoryDto(
    val id: UUID,
    val courtName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val mode: String,
    val priceDt: BigDecimal,
    val paymentStatus: String,
    val status: String,
    val createdAt: Instant,
)
