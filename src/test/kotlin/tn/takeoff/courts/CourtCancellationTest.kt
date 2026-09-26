package tn.takeoff.courts

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The 24-hour boundary, pinned with a controlled clock.
 *
 * The rule used to live inline as `Duration.toHours() < 24`, which lands on the
 * right answer only because truncation rounds the way we need. These cases fix
 * the boundary so a future rewrite cannot move it by a second without failing.
 */
class CourtCancellationTest {

    private val organiser = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val start = Instant.parse("2026-10-15T09:00:00Z")

    private fun booking(status: BookingStatus = BookingStatus.CONFIRMED) = CourtBooking(
        courtId = UUID.randomUUID(),
        userId = organiser,
        startsAt = start,
        endsAt = start.plus(Duration.ofMinutes(90)),
        mode = BookingMode.FULL,
        priceDt = BigDecimal("80.000"),
        status = status,
    )

    @Test
    fun `exactly 24 hours before is still allowed`() {
        val now = start.minus(Duration.ofHours(24))
        assertTrue(CourtCancellation.forMember(booking(), organiser, false, now).allowed)
    }

    @Test
    fun `a second earlier than the deadline is allowed`() {
        val now = start.minus(Duration.ofHours(24)).minusSeconds(1)
        assertTrue(CourtCancellation.forMember(booking(), organiser, false, now).allowed)
    }

    @Test
    fun `a second past the deadline is refused, and says why`() {
        val now = start.minus(Duration.ofHours(24)).plusSeconds(1)
        val d = CourtCancellation.forMember(booking(), organiser, false, now)
        assertFalse(d.allowed)
        assertEquals(CourtCancellation.Refusal.TOO_LATE, d.refusal)
        // The member is told the club can still do it, not just "no".
        assertTrue(d.reason!!.contains("club"), d.reason!!)
    }

    @Test
    fun `the deadline reported is the one enforced`() {
        val d = CourtCancellation.forMember(
            booking(), organiser, false, start.minus(Duration.ofDays(3)),
        )
        assertEquals(start.minus(Duration.ofHours(24)), d.deadline)
    }

    @Test
    fun `a started match can no longer be cancelled by a member`() {
        val d = CourtCancellation.forMember(booking(), organiser, false, start)
        assertFalse(d.allowed)
        assertEquals(CourtCancellation.Refusal.ALREADY_STARTED, d.refusal)
    }

    @Test
    fun `an already cancelled match is refused as such`() {
        val d = CourtCancellation.forMember(
            booking(BookingStatus.CANCELLED), organiser, false, start.minus(Duration.ofDays(3)),
        )
        assertFalse(d.allowed)
        assertEquals(CourtCancellation.Refusal.ALREADY_CANCELLED, d.refusal)
    }

    @Test
    fun `someone with no seat cannot cancel`() {
        val d = CourtCancellation.forMember(
            booking(), other, isSeated = false, now = start.minus(Duration.ofDays(3)),
        )
        assertFalse(d.allowed)
        assertEquals(CourtCancellation.Refusal.NOT_INVOLVED, d.refusal)
    }

    @Test
    fun `a seated player may leave, under the same deadline`() {
        val early = start.minus(Duration.ofDays(3))
        assertTrue(CourtCancellation.forMember(booking(), other, isSeated = true, now = early).allowed)

        val late = start.minus(Duration.ofHours(24)).plusSeconds(1)
        val d = CourtCancellation.forMember(booking(), other, isSeated = true, now = late)
        assertFalse(d.allowed)
        assertEquals(CourtCancellation.Refusal.TOO_LATE, d.refusal)
    }

    @Test
    fun `every refusal maps to an error code`() {
        for (refusal in CourtCancellation.Refusal.entries) {
            assertTrue(CourtCancellation.codeFor(refusal).startsWith("takeoff."))
        }
    }
}
