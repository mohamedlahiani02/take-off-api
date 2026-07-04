package tn.takeoff.classes

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import tn.takeoff.coaches.CoachActivity
import tn.takeoff.coaches.CoachRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Keeps the public Pilates schedule looking full without ever going stale.
 *
 * A static SQL seed would only cover the week it was written; the frontend
 * always fetches the *current* Mon–Sun. So instead we generate sessions from a
 * weekly template on every boot, rolling [WEEKS_AHEAD] weeks forward from the
 * start of the current week. It is idempotent — a session is only created when
 * none already exists for that class type at that exact start time — so repeated
 * restarts never duplicate rows, and admin-created sessions are left untouched.
 *
 * Opt out with SEED_PILATES_SCHEDULE=false (e.g. a real production tenant).
 * Runs after AdminSeeder / Flyway via a late @Order.
 */
@Component
@Order(100)
class PilatesScheduleSeeder(
    private val sessions: ClassSessionRepository,
    private val types: ClassTypeRepository,
    private val coaches: CoachRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val zone = ZoneId.of("Africa/Tunis")

    // A slot: local start time + the class-type name to run + which instructor
    // (index into the PILATES coach list, wrapped) teaches it.
    private data class Slot(val time: LocalTime, val typeName: String, val coachIdx: Int)

    override fun run(args: ApplicationArguments) {
        if (System.getenv("SEED_PILATES_SCHEDULE")?.equals("false", ignoreCase = true) == true) return

        val classTypes = types.findByActiveOrderByDisplayOrder(true)
        if (classTypes.isEmpty()) return
        val typeByName = classTypes.associateBy { it.name }
        val instructors = coaches.findByActivityAndActiveOrderByDisplayOrder(CoachActivity.PILATES, true)

        val today = LocalDate.now(zone)
        val weekStart = today.with(DayOfWeek.MONDAY)
        val rangeStart = weekStart.atStartOfDay(zone).toInstant()
        val rangeEnd = weekStart.plusWeeks(WEEKS_AHEAD).atStartOfDay(zone).toInstant()

        // Load everything already scheduled in the window once, key by
        // (classTypeId, startsAt), so we can skip anything that exists.
        val existing = sessions
            .findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(rangeStart, rangeEnd)
            .map { it.classTypeId to it.startsAt }
            .toHashSet()

        val now = Instant.now()
        var created = 0
        val toSave = mutableListOf<ClassSession>()

        for (week in 0 until WEEKS_AHEAD) {
            for ((dow, slots) in TEMPLATE) {
                val date = weekStart.plusWeeks(week.toLong()).with(dow)
                for (slot in slots) {
                    val startsAt = date.atTime(slot.time).atZone(zone).toInstant()
                    // Don't seed sessions in the past — they're not bookable and
                    // just clutter the schedule.
                    if (startsAt.isBefore(now)) continue
                    val type = typeByName[slot.typeName] ?: continue
                    if (!existing.add(type.id to startsAt)) continue
                    val instructor = if (instructors.isEmpty()) null
                        else instructors[slot.coachIdx % instructors.size]
                    toSave += ClassSession(
                        classTypeId = type.id,
                        instructorId = instructor?.id,
                        startsAt = startsAt,
                        durationMin = type.durationMin,
                        maxSpots = 8 + (slot.coachIdx % 3) * 2,   // 8 / 10 / 12
                        priceDt = type.defaultPriceDt,
                    )
                    created++
                }
            }
        }

        if (toSave.isNotEmpty()) sessions.saveAll(toSave)
        if (created > 0) log.info("PilatesScheduleSeeder: created {} rolling sessions ({} weeks ahead).", created, WEEKS_AHEAD)
    }

    companion object {
        private const val WEEKS_AHEAD = 5L

        private fun t(h: Int, m: Int) = LocalTime.of(h, m)

        // Weekdays run a packed day (07:00 → 19:30); weekends are lighter.
        private val WEEKDAY = listOf(
            Slot(t(7, 0),   "Sunrise Reformer", 0),
            Slot(t(8, 15),  "Reformer Flow",    1),
            Slot(t(9, 30),  "Mat Foundations",  2),
            Slot(t(11, 0),  "Sculpt & Tone",    0),
            Slot(t(12, 30),  "Slow Flow",       1),
            Slot(t(17, 0),  "Reformer Flow",    2),
            Slot(t(18, 15), "Sculpt & Tone",    0),
            Slot(t(19, 30), "Slow Flow",        1),
        )
        private val WEEKDAY_WITH_PRENATAL = WEEKDAY + Slot(t(10, 0), "Prenatal Pilates", 2)
        private val WEEKEND = listOf(
            Slot(t(8, 30),  "Sunrise Reformer", 0),
            Slot(t(10, 0),  "Reformer Flow",    1),
            Slot(t(11, 30), "Mat Foundations",  2),
            Slot(t(13, 0),  "Slow Flow",        0),
        )

        private val TEMPLATE: Map<DayOfWeek, List<Slot>> = mapOf(
            DayOfWeek.MONDAY to WEEKDAY,
            DayOfWeek.TUESDAY to WEEKDAY_WITH_PRENATAL,
            DayOfWeek.WEDNESDAY to WEEKDAY,
            DayOfWeek.THURSDAY to WEEKDAY_WITH_PRENATAL,
            DayOfWeek.FRIDAY to WEEKDAY,
            DayOfWeek.SATURDAY to WEEKEND,
            DayOfWeek.SUNDAY to WEEKEND,
        )
    }
}
