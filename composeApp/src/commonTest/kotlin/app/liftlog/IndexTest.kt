package app.liftlog

import app.liftlog.logic.*
import app.liftlog.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexTest {
    private val day = 86_400_000L

    @Test fun personalRecordsAreMarkedOnlyWhenBeaten() {
        val idx = Index(data(
            workout("w1", at(2026, 1, 1), item(BENCH, set(80.0, 8.0))),
            workout("w2", at(2026, 1, 8), item(BENCH, set(40.0, 10.0, t = "w"), set(85.0, 6.0))),
            workout("w3", at(2026, 1, 15), item(BENCH, set(70.0, 8.0))),
        ))
        assertEquals(0, idx.prm["w1"]!!.n)               // first session sets the baseline
        assertEquals(listOf("w", "orm"), idx.prm["w2"]!!.m["0:1"]) // heavier and a better 1RM, not more volume
        assertEquals(0, idx.prm["w3"]!!.n)
    }

    @Test fun historyIsNewestFirstAndPreviousSetsSkipTheCurrentWorkout() {
        val idx = Index(data(
            workout("w1", at(2026, 1, 1), item(BENCH, set(80.0, 8.0))),
            workout("w2", at(2026, 1, 8), item(BENCH, set(82.5, 8.0))),
        ))
        assertEquals(listOf("w2", "w1"), idx.h[BENCH]!!.map { it.wid })
        assertEquals(82.5, idx.prevSets(BENCH, at(2026, 1, 9), null)!![0].kg)
        assertEquals(80.0, idx.prevSets(BENCH, at(2026, 1, 9), "w2")!![0].kg)
        assertNull(idx.prevSets(BENCH, at(2025, 12, 1), null))
    }

    private fun active(vararg sets: WSet) = Active(id = "a", start = at(2026, 2, 1), exercises = listOf(item(BENCH, *sets)))

    @Test fun suggestionAddsWeightAtTheTopOfTheRepRange() {
        val idx = Index(data(workout("w1", at(2026, 1, 1), item(BENCH, set(80.0, 12.0)))))
        assertEquals(Sug(82.5, 8.0, true), idx.sugFor(active(set(null, null)), 0, 0))
    }

    @Test fun suggestionAddsARepBelowTheTop() {
        val idx = Index(data(workout("w1", at(2026, 1, 1), item(BENCH, set(80.0, 10.0)))))
        assertEquals(Sug(80.0, 11.0, false), idx.sugFor(active(set(null, null)), 0, 0))
    }

    @Test fun hardSetsHoldTheWeight() {
        val idx = Index(data(workout("w1", at(2026, 1, 1), item(BENCH, set(80.0, 12.0, rpe = 9.5)))))
        assertEquals(Sug(80.0, 12.0, false), idx.sugFor(active(set(null, null)), 0, 0))
    }

    @Test fun poundUsersGetFivePoundJumps() {
        val idx = Index(data(workout("w1", at(2026, 1, 1), item(BENCH, set(fromU(135.0, true), 12.0))), settings = Settings(unit = "lb")))
        assertEquals(140.0, r2(toU(idx.sugFor(active(set(null, null)), 0, 0)!!.kg!!, true)))
    }

    @Test fun suggestionsCanBeTurnedOffAndSkipWarmups() {
        val d = data(workout("w1", at(2026, 1, 1), item(BENCH, set(80.0, 12.0))))
        assertNull(Index(d.copy(settings = Settings(sug = false))).sugFor(active(set(null, null)), 0, 0))
        assertNull(Index(d).sugFor(active(set(null, null, t = "w")), 0, 0))
    }

    @Test fun livePrsFlagTheBestNewSet() {
        val idx = Index(data(workout("w1", at(2026, 1, 1), item(BENCH, set(80.0, 8.0)))))
        val a = active(set(80.0, 8.0, done = true), set(85.0, 8.0, done = true), set(90.0, 3.0))
        assertEquals(mapOf(1 to listOf("w", "orm", "vol")), idx.livePrs(a, 0)) // the unticked 90 doesn't count yet
    }

    @Test fun volumeCountsBodyweightOnlyWhenAskedTo() {
        val measures = listOf(Measure(id = "m1", date = at(2026, 1, 1), v = mapOf("weight_kg" to 80.0)))
        val w = workout("w1", at(2026, 1, 2), item(BENCH, set(100.0, 5.0)), item(PULLUP, set(null, 10.0)))
        assertEquals(500.0, Index(data(w, measures = measures)).wVol(w))
        assertEquals(1300.0, Index(data(w, measures = measures, settings = Settings(bwv = true))).wVol(w))
    }

    @Test fun lastRestIgnoresHevysZeroRest() {
        val idx = Index(data(
            workout("w1", at(2026, 1, 1), item(BENCH, set(80.0, 8.0), rest = 150)),
            workout("w2", at(2026, 1, 8), item(BENCH, set(80.0, 8.0), rest = 0), src = "hevy"),
        ))
        assertEquals(150, idx.lastRest(BENCH))
        assertEquals(90, idx.lastRest(PULLUP))
    }

    @Test fun routinesAreRebuiltFromRecentRepeatedWorkouts() {
        val now = nowMs()
        val idx = Index(data(
            workout("a1", now - 20 * day, item(BENCH, set(80.0, 8.0), rest = 120), title = "Push"),
            workout("a2", now - 10 * day, item(BENCH, set(82.5, 8.0), rest = 120), title = "Push"),
            workout("a3", now - 3 * day, item(BENCH, set(85.0, 8.0), rest = 120), item(PULLUP, set(null, 10.0)), title = "Push"),
            workout("b1", now - 2 * day, item(PULLUP, set(null, 10.0)), title = "Pull"),
        ))
        val rs = idx.routinesFromHistory()
        assertEquals(listOf("Push"), rs.map { it.name })
        assertEquals(listOf(BENCH, PULLUP), rs[0].exercises.map { it.eid }) // from the latest session
        assertEquals(85.0, rs[0].exercises[0].sets[0].kg)
        assertTrue(rs[0].exercises[0].sets.all { it.done == null })
        // An existing routine with the same name is not duplicated.
        val withRoutine = Index(idx.data.copy(routines = mapOf("r1" to Routine(id = "r1", name = "push"))))
        assertTrue(withRoutine.routinesFromHistory().isEmpty())
    }

    @Test fun stallNeedsEnoughSessions() {
        val now = nowMs()
        fun w(id: String, ago: Int, kg: Double) = workout(id, now - ago * day, item(BENCH, set(kg, 5.0)))
        val stalled = Index(data(w("o1", 60, 100.0), w("o2", 50, 100.0), w("n1", 20, 100.0), w("n2", 12, 97.5), w("n3", 4, 100.0)))
        assertTrue(stalled.stalled(BENCH))
        val improving = Index(data(w("o1", 60, 100.0), w("o2", 50, 100.0), w("n1", 20, 100.0), w("n2", 12, 102.5), w("n3", 4, 105.0)))
        assertFalse(improving.stalled(BENCH))
        assertFalse(Index(data(w("n1", 4, 100.0))).stalled(BENCH))
    }

    @Test fun weeklyStreaks() {
        val now = nowMs()
        val idx = Index(data(
            workout("w1", now, item(BENCH, set(80.0, 8.0))),
            workout("w2", now - 7 * day, item(BENCH, set(80.0, 8.0))),
            workout("w3", now - 35 * day, item(BENCH, set(80.0, 8.0))),
        ))
        assertEquals(2, idx.weekRuns().first)
        assertEquals(2, idx.weekRuns().second)
    }

    @Test fun singleSupersetMembersAreCleared() {
        val items = listOf(item(BENCH, ss = 1), item(PULLUP, ss = 1), item(PLANK, ss = 2))
        assertEquals(listOf(1, 1, null), normSS(items).map { it.ss })
        assertNotNull(ssInfo(items)(1))
    }
}
