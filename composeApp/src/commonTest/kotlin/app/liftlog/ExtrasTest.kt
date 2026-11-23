package app.liftlog

import app.liftlog.logic.*
import app.liftlog.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtrasTest {
    @Test fun tapeShowsCmWithKgAndInchesWithLb() {
        assertEquals("cm", mUnit("waist_in", false)); assertEquals("in", mUnit("waist_in", true))
        assertEquals("kg", mUnit("weight_kg", false)); assertEquals("%", mUnit("fat_percent", false))
        assertEquals("81.3 cm", mFmt("waist_in", 32.0, false))
        assertEquals("32 in", mFmt("waist_in", 32.0, true))
        // Typed in cm, stored in inches, shown back as typed.
        val stored = mStore("waist_in", 81.3, false)
        assertEquals(32.008, stored)
        assertEquals(81.3, r1(mShow("waist_in", stored, false)))
        assertEquals(18.0, mStore("fat_percent", 18.0, false))
    }

    @Test fun warmupsRampToTheWorkingWeight() {
        // 100 kg barbell: bar, then about 50/70/85 %, rounded to 2.5 kg.
        assertEquals(listOf(20.0 to 10.0, 50.0 to 8.0, 70.0 to 5.0, 85.0 to 3.0), warmupPlan(100.0, 20.0, false))
        // Light working weight: no bar set, nothing below the bar, nothing repeated.
        assertEquals(listOf(20.0 to 8.0, 25.0 to 3.0), warmupPlan(30.0, 20.0, false))
        // Dumbbells (no bar) in lb round to 5 lb.
        assertEquals(listOf(25.0 to 8.0, 35.0 to 5.0, 45.0 to 3.0), warmupPlan(50.0, 0.0, true))
        assertTrue(warmupPlan(2.5, 0.0, false).isEmpty())
    }

    @Test fun recordsListWhatWasBeaten() {
        val w1 = workout("w1", at(2026, 3, 2), item(BENCH, set(60.0, 8.0)))
        val w2 = workout("w2", at(2026, 3, 5), item(BENCH, set(20.0, 10.0, t = "w"), set(62.5, 8.0)))
        val idx = Index(data(w1, w2))
        assertTrue(workoutRecords(idx, w1, false).isEmpty(), "first session sets no records")
        val r = workoutRecords(idx, w2, false)
        assertEquals(listOf("Heaviest weight 62.5 kg", "Best 1RM 79.2 kg", "Best set volume 500 kg"), r.map { it.what })
        assertTrue(r.all { it.exercise == "Bench Press (Barbell)" })
    }

    @Test fun liveRecordsFollowTickedSets() {
        val w1 = workout("w1", at(2026, 3, 2), item(BENCH, set(60.0, 8.0)))
        val idx = Index(data(w1))
        val a = Active(id = "a", start = at(2026, 3, 5), exercises = listOf(item(BENCH, set(65.0, 5.0, done = true), set(70.0, 5.0))))
        val r = liveRecords(idx, a, false)
        assertEquals(listOf("Heaviest weight 65 kg"), r.map { it.what }, "only ticked sets count; 1RM and volume aren't beaten")
    }

    @Test fun shareCardSummarisesTheWorkout() {
        val w1 = workout("w1", at(2026, 3, 2), item(BENCH, set(60.0, 8.0)))
        val w2 = workout("w2", at(2026, 3, 5), item(BENCH, set(62.5, 8.0), set(60.0, 8.0)), item(PULLUP, set(null, 10.0)), title = "Push day")
        val c = shareCard(Index(data(w1, w2)), w2, false, true)
        assertEquals("Push day", c.title); assertEquals("1h 0m", c.duration); assertEquals("3", c.sets)
        assertEquals("Bench Press (Barbell)", c.lines[0].name)
        assertTrue(c.lines[0].detail.startsWith("2 sets · best 62.5 kg"), c.lines[0].detail)
        assertEquals(3, c.records.size); assertEquals(0, c.more)
    }

    @Test fun widgetCountsThisWeekAndStreak() {
        val now = at(2026, 3, 11, 9) // a Wednesday
        val thisWeek = weekStart(now); val last = addDays(thisWeek, -7); val twoAgo = addDays(thisWeek, -14)
        val recent = listOf(at(2026, 3, 9, 7) to 3_600_000L, at(2026, 3, 10, 7) to 2_700_000L, at(2026, 3, 6, 7) to 3_600_000L)
        val n = widgetNumbers(listOf(twoAgo, last, thisWeek), recent, now)
        assertEquals(2, n.workouts); assertEquals(105, n.minutes); assertEquals(3, n.streak)
        // Nothing yet this week: the streak still counts up to last week.
        assertEquals(2, widgetNumbers(listOf(twoAgo, last), emptyList(), now).streak)
        assertEquals(0, widgetNumbers(listOf(twoAgo), emptyList(), now).streak)
    }

    @Test fun nextRoutineRotates() {
        val r1 = Routine(id = "r1", name = "Push", created = 1); val r2 = Routine(id = "r2", name = "Pull", created = 2); val r3 = Routine(id = "r3", name = "Legs", created = 3)
        val rs = listOf(r1, r2, r3)
        assertEquals("r1", nextRoutine(Index(data(routines = rs)))?.id)
        val did = workout("w1", at(2026, 3, 2), item(BENCH, set(60.0, 8.0))).copy(routineId = "r2")
        assertEquals("r3", nextRoutine(Index(data(did, routines = rs)))?.id)
        val wrap = did.copy(routineId = "r3")
        assertEquals("r1", nextRoutine(Index(data(wrap, routines = rs)))?.id)
        assertNull(nextRoutine(Index(data())))
    }
}
