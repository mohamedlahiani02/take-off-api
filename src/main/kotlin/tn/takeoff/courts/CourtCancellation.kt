package tn.takeoff.courts

import java.time.Duration
import java.time.Instant

/**
 * Who may cancel a court booking, and why not.
 *
 * One place decides, so the screen and the API cannot disagree about where the
 * deadline falls. The member rule is the one already in force: a member may
 * cancel up to the cut-off; inside it, only the club can.
 *
 * The boundary was previously expressed as `Duration.toHours() < 24`, which is
 * right only because truncation happens to round the way we need. Comparing
 * Durations states the rule instead of relying on that.
 *
 * NOTE ON WORDING — the business phrase "after 24h" is ambiguous: it can mean
 * "at least 24 hours before the match" (implemented here, and what the code has
 * always done) or "within 24 hours of booking". They differ for a match booked
 * weeks ahead. This keeps the existing meaning; changing it is a product
 * decision, not a refactor.
 */
object CourtCancellation {

    /** A member must act at least this long before the match starts. */
    val MEMBER_NOTICE: Duration = Duration.ofHours(24)

    enum class Refusal {
        /** The match is already cancelled. */
        ALREADY_CANCELLED,

        /** The caller is neither the organiser nor a seated player. */
        NOT_INVOLVED,

        /** Inside the notice window: the club can still do it. */
        TOO_LATE,

        /** The match has already started or finished. */
        ALREADY_STARTED,
    }

    data class Decision(
        val allowed: Boolean,
        val refusal: Refusal? = null,
        /** Wording the UI can show as-is. */
        val reason: String? = null,
        /** When the member's own right to cancel ends. */
        val deadline: Instant? = null,
    )

    /**
     * Whether [userId] may cancel [booking] right now.
     *
     * @param isSeated true when the caller holds a seat but did not organise the
     *   match — they are leaving, which frees only their own place.
     */
    fun forMember(
        booking: CourtBooking,
        userId: java.util.UUID,
        isSeated: Boolean,
        now: Instant = Instant.now(),
    ): Decision {
        val deadline = booking.startsAt.minus(MEMBER_NOTICE)

        if (booking.status == BookingStatus.CANCELLED) {
            return Decision(false, Refusal.ALREADY_CANCELLED, "This match is already cancelled.")
        }
        if (booking.userId != userId && !isSeated) {
            return Decision(false, Refusal.NOT_INVOLVED, "This is not your match.")
        }
        if (!booking.startsAt.isAfter(now)) {
            return Decision(
                false, Refusal.ALREADY_STARTED,
                "This match has already started.", deadline,
            )
        }
        // Allowed at exactly the cut-off; refused one second later.
        if (now.isAfter(deadline)) {
            return Decision(
                false, Refusal.TOO_LATE,
                "Cancelling is only possible up to ${MEMBER_NOTICE.toHours()} hours before the match. " +
                    "Call the club and they can still cancel it for you.",
                deadline,
            )
        }
        return Decision(true, deadline = deadline)
    }

    /** The error code paired with a refusal, for the API's problem response. */
    fun codeFor(refusal: Refusal): String = when (refusal) {
        Refusal.ALREADY_CANCELLED -> "takeoff.court.already_cancelled"
        Refusal.NOT_INVOLVED -> "takeoff.forbidden"
        Refusal.TOO_LATE -> "takeoff.court.cancel_too_late"
        Refusal.ALREADY_STARTED -> "takeoff.court.already_started"
    }
}
