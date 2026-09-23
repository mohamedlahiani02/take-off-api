package tn.takeoff.courts

import tn.takeoff.common.errors.BadRequestException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The single authoritative definition of when a padel court can be booked.
 *
 * Availability listing, member booking and admin booking all resolve slots through here so the
 * three can never disagree. Everything is anchored to the club's wall clock in Sfax
 * (Africa/Tunis) rather than UTC — the same instant is a different slot in a different offset.
 */
object CourtSlots {

    val TUNIS: ZoneId = ZoneId.of("Africa/Tunis")
    val SLOT_DURATION: Duration = Duration.ofMinutes(90)
    val OPEN: LocalTime = LocalTime.of(7, 0)
    val CLOSE: LocalTime = LocalTime.of(22, 0)

    /**
     * Every bookable slot start on a given Tunis calendar day, in order.
     *
     * The grid walks forward from [OPEN] in [SLOT_DURATION] steps and stops as soon as a slot
     * would run past [CLOSE], so the final slot always ends exactly at closing time.
     */
    fun startsFor(date: LocalDate): List<ZonedDateTime> {
        val starts = mutableListOf<ZonedDateTime>()
        var cursor = ZonedDateTime.of(date, OPEN, TUNIS)
        val close = ZonedDateTime.of(date, CLOSE, TUNIS)
        while (!cursor.plus(SLOT_DURATION).isAfter(close)) {
            starts.add(cursor)
            cursor = cursor.plus(SLOT_DURATION)
        }
        return starts
    }

    /**
     * True when [startsAt] is exactly one of the grid starts for the Tunis day it falls on.
     *
     * Comparing against the generated grid — rather than checking open/close times separately —
     * rejects both off-grid starts (07:17) and slots that would run past midnight (23:30), since
     * neither is ever produced by [startsFor].
     */
    fun isBookableStart(startsAt: Instant): Boolean {
        val zoned = startsAt.atZone(TUNIS)
        return startsFor(zoned.toLocalDate()).any { it.toInstant() == startsAt }
    }

    /**
     * Guard for every write path. Throws [BadRequestException] when the requested start is not a
     * real slot: outside operating hours, off the 90-minute grid, or crossing closing/midnight.
     */
    fun requireBookableStart(startsAt: Instant) {
        if (!isBookableStart(startsAt)) {
            val local = startsAt.atZone(TUNIS)
            throw BadRequestException(
                "takeoff.court.invalid_slot",
                "Bookings run in ${SLOT_DURATION.toMinutes()}-minute slots from $OPEN to $CLOSE " +
                    "Tunis time; ${local.toLocalTime()} on ${local.toLocalDate()} is not a slot start",
            )
        }
    }
}
