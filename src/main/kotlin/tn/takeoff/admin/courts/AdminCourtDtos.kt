package tn.takeoff.admin.courts

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tn.takeoff.courts.BookingMode
import tn.takeoff.courts.BookingStatus
import tn.takeoff.courts.Court
import tn.takeoff.courts.CourtBlock
import tn.takeoff.courts.CourtBooking
import tn.takeoff.courts.CourtBookingPayment
import tn.takeoff.courts.CourtBookingPlayer
import tn.takeoff.courts.GuestPaymentMethod
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
    val userId: UUID?,
    val userName: String?,
    val shareDt: BigDecimal,
    val paymentStatus: PlayerPaymentStatus,
    val paymentMethod: PlayerPaymentMethod?,
    val paidAt: Instant?,
    val noShow: Boolean,
) {
    companion object {
        fun from(p: CourtBookingPlayer, userName: String? = null) = ParticipantDto(
            // A participant is either a member or a named guest.
            id = p.id, userId = p.userId, userName = userName ?: p.guestName, shareDt = p.shareDt,
            paymentStatus = p.paymentStatus, paymentMethod = p.paymentMethod,
            paidAt = p.paidAt, noShow = p.noShow,
        )
    }
}

/**
 * Money collected against a booking with no named seat behind it (a guest
 * covering several remaining tranches at once). Kept distinct from
 * ParticipantDto: this is a payment record, not an identity.
 */
data class GuestPaymentDto(
    val id: UUID,
    val amountDt: BigDecimal,
    val method: GuestPaymentMethod,
    val payerName: String?,
    val coveredSeats: Int,
    val reference: String?,
    val voided: Boolean,
    val voidReason: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(p: CourtBookingPayment) = GuestPaymentDto(
            id = p.id, amountDt = p.amountDt, method = p.method, payerName = p.payerName,
            coveredSeats = p.coveredSeats, reference = p.reference, voided = p.voided,
            voidReason = p.voidReason, createdAt = p.createdAt,
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
    val guestPayments: List<GuestPaymentDto> = emptyList(),
    val paymentState: BookingPaymentState = BookingPaymentState.UNPAID,
    /** What the whole court is worth, independent of mode (FULL or all 4 SHARE seats). */
    val totalDueDt: BigDecimal = BigDecimal.ZERO,
    /** What has actually been collected: settled seats + active guest payments. */
    val totalCollectedDt: BigDecimal = BigDecimal.ZERO,
    val seatsOpenForSale: Int = 0,
) {
    companion object {
        // A padel SHARE booking is a 4-player match; FULL (or non-padel) is a single payer.
        const val SHARE_SLOTS = 4

        /**
         * `totalDueDt` is the whole court's value (pass CourtPricing.fullPrice()
         * for a SHARE booking; for FULL, `b.priceDt` already equals it — the
         * organiser's own tranche IS the whole court).
         *
         * paymentState is computed from money actually collected, not a count of
         * settled seats: a guest's lump cash-in covers seats with no player row
         * behind them at all, so counting settled *players* would show a fully
         * paid court as forever partial.
         */
        fun from(
            b: CourtBooking,
            userName: String? = null,
            players: List<CourtBookingPlayer> = emptyList(),
            playerNames: Map<UUID, String> = emptyMap(),
            guestPayments: List<CourtBookingPayment> = emptyList(),
            totalDueDt: BigDecimal = b.priceDt,
        ): BookingDto {
            val slots = if (b.mode == BookingMode.SHARE) SHARE_SLOTS else 1
            val activePayments = guestPayments.filter { !it.voided }

            val fromPlayers = players
                .filter { it.paymentStatus == PlayerPaymentStatus.PAID || it.paymentStatus == PlayerPaymentStatus.COVERED }
                .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.shareDt) }
            val fromGuests = activePayments.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amountDt) }
            val collected = fromPlayers.add(fromGuests)

            val state = when {
                collected >= totalDueDt && totalDueDt > BigDecimal.ZERO -> BookingPaymentState.PAID
                collected > BigDecimal.ZERO -> BookingPaymentState.PARTIAL
                else -> BookingPaymentState.UNPAID
            }

            val coveredByGuests = activePayments.sumOf { it.coveredSeats }
            val openForSale = if (b.mode == BookingMode.SHARE) (slots - players.size - coveredByGuests).coerceAtLeast(0) else 0

            return BookingDto(
                id = b.id, courtId = b.courtId, userId = b.userId, userName = userName,
                startsAt = b.startsAt, endsAt = b.endsAt, mode = b.mode,
                priceDt = b.priceDt, paymentStatus = b.paymentStatus,
                paymentMethod = b.paymentMethod, status = b.status, cancelReason = b.cancelReason,
                participants = players.map { ParticipantDto.from(it, playerNames[it.userId]) },
                guestPayments = guestPayments.map(GuestPaymentDto::from),
                paymentState = state,
                totalDueDt = totalDueDt,
                totalCollectedDt = collected,
                seatsOpenForSale = openForSale,
            )
        }
    }
}

/**
 * Admin collects cash (or card/D17) from someone at the desk for the
 * remaining balance of a booking — a guest with no account, possibly
 * covering several friends' seats in one payment. `coveredSeats` reserves
 * that many open seats so they stop being offered for booking; leave it 0 for
 * a payment that is just money with no seat claim (or on a FULL booking,
 * which has no seats to reserve).
 */
data class AddGuestPaymentRequest(
    @field:NotNull val amountDt: BigDecimal,
    @field:NotNull val method: tn.takeoff.courts.GuestPaymentMethod,
    val payerName: String? = null,
    val coveredSeats: Int = 0,
    val reference: String? = null,
)

/** Corrections are traced, never a silent delete — reason is mandatory. */
data class VoidGuestPaymentRequest(@field:NotBlank val reason: String)

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
    val userId: UUID?,
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
