package tn.takeoff.courts

import org.junit.jupiter.api.Test
import tn.takeoff.common.errors.BadRequestException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression cover for slot validation. The old check compared the start and end *times of day*
 * independently, so a 23:30 start whose end rolled past midnight to 01:00 looked like it was
 * inside 07:00-22:00, and any off-grid minute was accepted.
 */
class CourtSlotsTest {

    private val day = LocalDate.of(2026, 10, 12)
    private fun tunis(h: Int, m: Int) = ZonedDateTime.of(day, LocalTime.of(h, m), CourtSlots.TUNIS).toInstant()

    @Test
    fun `the grid runs from opening to closing in ninety minute steps`() {
        val starts = CourtSlots.startsFor(day).map { it.toLocalTime() }
        assertEquals(
            listOf(
                LocalTime.of(7, 0), LocalTime.of(8, 30), LocalTime.of(10, 0), LocalTime.of(11, 30),
                LocalTime.of(13, 0), LocalTime.of(14, 30), LocalTime.of(16, 0), LocalTime.of(17, 30),
                LocalTime.of(19, 0), LocalTime.of(20, 30),
            ),
            starts,
        )
    }

    @Test
    fun `the last slot ends exactly at closing time`() {
        val last = CourtSlots.startsFor(day).last()
        assertEquals(CourtSlots.CLOSE, last.plus(CourtSlots.SLOT_DURATION).toLocalTime())
    }

    @Test
    fun `every generated start is bookable`() {
        for (start in CourtSlots.startsFor(day)) {
            assertTrue(CourtSlots.isBookableStart(start.toInstant()), "grid start $start rejected")
        }
    }

    @Test
    fun `a slot that would cross midnight is rejected`() {
        // 23:30 + 90min = 01:00 the next day. The old time-of-day comparison let this through.
        assertFalse(CourtSlots.isBookableStart(tunis(23, 30)))
        assertFailsWith<BadRequestException> { CourtSlots.requireBookableStart(tunis(23, 30)) }
    }

    @Test
    fun `an off-grid start is rejected`() {
        assertFalse(CourtSlots.isBookableStart(tunis(7, 17)))
        assertFalse(CourtSlots.isBookableStart(tunis(8, 0)))
        assertFailsWith<BadRequestException> { CourtSlots.requireBookableStart(tunis(7, 17)) }
    }

    @Test
    fun `starts before opening or after the last slot are rejected`() {
        assertFalse(CourtSlots.isBookableStart(tunis(6, 0)))
        assertFalse(CourtSlots.isBookableStart(tunis(5, 30)))
        // 22:00 is closing time, not a slot start.
        assertFalse(CourtSlots.isBookableStart(tunis(22, 0)))
        // 21:00 would end at 22:30, past closing.
        assertFalse(CourtSlots.isBookableStart(tunis(21, 0)))
    }

    @Test
    fun `validation follows Tunis wall clock, not UTC`() {
        // 06:00 UTC is 07:00 in Tunis (UTC+1) and therefore the first slot of the day.
        val sixUtc = ZonedDateTime.of(day, LocalTime.of(6, 0), java.time.ZoneOffset.UTC).toInstant()
        assertTrue(CourtSlots.isBookableStart(sixUtc))
        // 07:00 UTC is 08:00 in Tunis, which is not on the grid.
        val sevenUtc = ZonedDateTime.of(day, LocalTime.of(7, 0), java.time.ZoneOffset.UTC).toInstant()
        assertFalse(CourtSlots.isBookableStart(sevenUtc))
    }
}
