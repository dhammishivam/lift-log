package app.liftlog

import app.liftlog.logic.nowMs
import app.liftlog.model.*
import app.liftlog.ui.Screen
import app.liftlog.ui.Tab
import app.liftlog.ui.checkSet
import app.liftlog.ui.openFinish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The workout session, saving, imports and exports, run against a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppStateTest {
    private val now = nowMs()
    private val push = Routine(id = "r_push", name = "Push", created = 1, exercises = listOf(bench(listOf(WSet(kg = 60.0, reps = 8.0), WSet(kg = 60.0, reps = 8.0)))))
    private fun lastBench(kg: Double = 60.0) = Workout(id = "w_old", title = "Push", start = now - 3 * DAY, end = now - 3 * DAY + 3_600_000, routineId = "r_push",
        exercises = listOf(bench(listOf(WSet(kg = kg, reps = 8.0)))))

    /* ---------- starting ---------- */

    @Test fun startingARoutineCopiesItsSetsAsTargets() {
        val (st, _) = loadedState(Seed().routine(push))
        st.handleAction("start:r_push")
        val a = st.data.active!!
        assertEquals("Push", a.title); assertEquals("r_push", a.routineId)
        assertEquals(listOf(SetVals(60.0, 8.0), SetVals(60.0, 8.0)), a.exercises[0].sets.map { it.p })
        assertTrue(a.exercises[0].sets.all { it.done == null && it.kg == null })
        assertTrue(st.stack.last() is Screen.Live); assertEquals(Tab.Workout, st.tab)
    }

    @Test fun startingWhileAWorkoutIsOpenAsksFirst() {
        val (st, _) = loadedState(Seed().routine(push).active(Active(id = "a1", start = now - 60_000, title = "Legs")))
        st.handleAction("empty")
        assertEquals("Legs", st.data.active!!.title)
        assertEquals("You have a workout in progress", st.menu!!.title)
        st.menu!!.items.first { it.label.startsWith("Discard") }.action()
        assertEquals("", st.data.active!!.title)
    }

    /* ---------- ticking sets ---------- */

    @Test fun tickingASetFillsItsHintsAndStartsRest() {
        val (st, p) = loadedState(Seed().routine(push))
        st.handleAction("start:r_push")
        checkSet(st, 0, 0)
        val s = st.data.active!!.exercises[0].sets[0]
        assertEquals(60.0, s.kg); assertEquals(8.0, s.reps); assertEquals(true, s.done)
        assertTrue(st.restEnd > now); assertNotNull(p.restEnds.last())
        checkSet(st, 0, 0)
        assertEquals(false, st.data.active!!.exercises[0].sets[0].done)
    }

    @Test fun anEmptySetNeedsAValueFirst() {
        val (st, _) = loadedState(Seed().active(Active(id = "a1", start = now, exercises = listOf(bench(listOf(WSet()))))))
        checkSet(st, 0, 0)
        assertEquals("Enter a value first", st.toast!!.text)
        assertNull(st.data.active!!.exercises[0].sets[0].done)
    }

    @Test fun aWeightedSetNeedsWeightAndReps() {
        val (st, _) = loadedState(Seed().active(Active(id = "a1", start = now, exercises = listOf(bench(listOf(WSet(reps = 8.0), WSet(kg = 60.0)))))))
        checkSet(st, 0, 0)
        assertEquals("Add the weight first", st.toast!!.text)
        checkSet(st, 0, 1)
        assertEquals("Add the reps first", st.toast!!.text)
        assertTrue(st.data.active!!.exercises[0].sets.none { it.done == true })
        assertEquals(0L, st.restEnd)
    }

    @Test fun supersetsSkipRestUntilTheLastExercise() {
        val a = Active(id = "a1", start = now, exercises = listOf(bench(listOf(WSet(kg = 50.0, reps = 10.0))).copy(ss = 1), squat(listOf(WSet(kg = 80.0, reps = 5.0))).copy(ss = 1)))
        val (st, _) = loadedState(Seed().active(a))
        checkSet(st, 0, 0)
        assertEquals("Superset: next is Squat (Barbell)", st.toast!!.text); assertEquals(0L, st.restEnd)
        checkSet(st, 1, 0)
        assertTrue(st.restEnd > 0)
    }

    @Test fun aRecordGetsTheRightMascotOrAToast() {
        // Typed values (hints would suggest 62.5 kg, last time plus 2.5).
        val a = Active(id = "a1", start = now, routineId = "r_push", exercises = listOf(bench(listOf(WSet(kg = 65.0, reps = 8.0)))))
        val (st, p) = loadedState(Seed().workout(lastBench()).active(a))
        checkSet(st, 0, 0)
        val c = st.celebration!!
        assertEquals("titan", c.mascot); assertEquals("New record!", c.title)
        assertTrue(c.line, c.line.contains("Heaviest weight: 65 kg")); assertEquals(1, p.buzzes)

        val (st2, _) = loadedState(Seed().workout(lastBench()).active(a).settings(Settings(cele = false)))
        checkSet(st2, 0, 0)
        assertNull(st2.celebration); assertTrue(st2.toast!!.text.startsWith("🏆 New record: Bench Press (Barbell)"))
    }

    @Test fun matchingLastSessionIsNotARecord() {
        val a = Active(id = "a1", start = now, exercises = listOf(bench(listOf(WSet(kg = 60.0, reps = 8.0)))))
        val (st, _) = loadedState(Seed().workout(lastBench()).active(a))
        checkSet(st, 0, 0)
        assertNull(st.celebration)
    }

    @Test fun hintsSuggestTheNextWeightAfterAGoodSession() {
        val a = Active(id = "a1", start = now, exercises = listOf(bench(listOf(WSet(p = SetVals(60.0, 8.0))))))
        val (st, _) = loadedState(Seed().workout(lastBench()).active(a))
        checkSet(st, 0, 0)
        assertEquals(62.5, st.data.active!!.exercises[0].sets[0].kg)
    }

    /* ---------- notification ---------- */

    @Test fun notificationShowsTheNextSetAndProgress() {
        val a = Active(id = "a1", start = now - 600_000, title = "Push", exercises = listOf(bench(listOf(WSet(t = "w", p = SetVals(20.0, 10.0)), WSet(p = SetVals(60.0, 8.0)), WSet(p = SetVals(60.0, 8.0))))))
        val (st, _) = loadedState(Seed().active(a))
        var i = st.liveInfo()!!
        assertEquals("Push", i.title); assertEquals(0, i.done); assertEquals(3, i.total); assertNull(i.restEnd)
        assertEquals("Bench Press (Barbell) · warm-up · 20 kg × 10", i.next)
        // Skip the warm-up: "next" follows the set you just did, not the skipped one.
        checkSet(st, 0, 1)
        i = st.liveInfo()!!
        assertEquals("Bench Press (Barbell) · set 2 · 60 kg × 8", i.next); assertEquals(1, i.done); assertNotNull(i.restEnd)
        checkSet(st, 0, 2)
        assertEquals("Bench Press (Barbell) · warm-up · 20 kg × 10", st.liveInfo()!!.next)
        checkSet(st, 0, 0)
        assertNull(st.liveInfo()!!.next)
    }

    @Test fun editingAWorkoutHasNoNotification() {
        val (st, _) = loadedState(Seed().active(Active(id = "w1", editId = "w1", start = now - DAY)))
        assertNull(st.liveInfo())
    }

    @Test fun notificationButtonsWork() {
        val (st, _) = loadedState(Seed().routine(push))
        st.handleAction("start:r_push")
        st.handleAction("done")
        assertEquals(true, st.data.active!!.exercises[0].sets[0].done)
        val end = st.restEnd
        st.handleAction("plus"); assertEquals(end + 15_000, st.restEnd)
        st.handleAction("minus"); st.handleAction("minus"); assertEquals(end - 15_000, st.restEnd)
        st.handleAction("skip"); assertEquals(0L, st.restEnd)
        st.handleAction("done")
        assertEquals(true, st.data.active!!.exercises[0].sets[1].done)
    }

    @Test fun restRunningOutAlertsOnce() {
        val (st, p) = loadedState(Seed().routine(push))
        st.handleAction("start:r_push"); checkSet(st, 0, 0)
        st.restEnd = nowMs() - 1
        st.tick()
        assertEquals(0L, st.restEnd); assertEquals(1, p.alerts); assertEquals("Rest over. Next set!", st.toast!!.text)
        st.tick(); assertEquals(1, p.alerts)
    }

    @Test fun shiftingRestBelowZeroStopsIt() {
        val (st, _) = loadedState()
        st.startRest(10)
        st.shiftRest(-15_000)
        assertEquals(0L, st.restEnd)
    }

    /* ---------- finishing ---------- */

    private fun finish(st: app.liftlog.ui.AppState): Screen.Finish { openFinish(st); return st.stack.last() as Screen.Finish }

    @Test fun finishingNeedsATickedSet() {
        val (st, _) = loadedState(Seed().routine(push))
        st.handleAction("start:r_push")
        openFinish(st)
        assertEquals("Complete at least one set first", st.toast!!.text)
        assertFalse(st.stack.last() is Screen.Finish)
    }

    @Test fun savingKeepsOnlyTickedSetsAndCleansThem() {
        val (st, p) = loadedState(Seed().routine(push))
        st.handleAction("start:r_push"); checkSet(st, 0, 0)
        val f = finish(st); f.title = "Chest day"; f.notes = "Good"
        st.saveWorkout(f)
        val w = st.data.workouts.values.single()
        assertEquals("Chest day", w.title); assertEquals("Good", w.notes); assertEquals("r_push", w.routineId)
        assertEquals(listOf(WSet(kg = 60.0, reps = 8.0)), w.exercises[0].sets)
        assertNull(st.data.active); assertEquals(0L, st.restEnd)
        assertEquals(Tab.History, st.tab); assertTrue((st.stack.single() as Screen.WorkoutDetail).id == w.id)
        assertTrue(st.toast!!.text.startsWith("Workout saved"))
        assertNull(p.restEnds.last())
    }

    @Test fun savingWithRecordsCelebratesWithChamp() {
        val (st, _) = loadedState(Seed().routine(push).workout(lastBench(55.0)))
        st.handleAction("start:r_push"); checkSet(st, 0, 0)
        st.celebration = null
        st.saveWorkout(finish(st))
        assertEquals("champ", st.celebration!!.mascot)
        assertEquals("3 new records!", st.celebration!!.title)
    }

    @Test fun savingCanUpdateTheRoutine() {
        val (st, _) = loadedState(Seed().routine(push))
        st.handleAction("start:r_push")
        st.updateActive { a -> a.copy(exercises = a.exercises.map { e -> e.copy(sets = e.sets.map { s -> s.copy(kg = 62.5, reps = 8.0, done = true) }) }) }
        val f = finish(st); f.upd = true
        st.saveWorkout(f)
        assertEquals(listOf(62.5, 62.5), st.data.routines["r_push"]!!.exercises[0].sets.map { it.kg })
    }

    @Test fun editingASavedWorkoutKeepsItsId() {
        val old = lastBench()
        val (st, _) = loadedState(Seed().workout(old))
        st.startWorkout { Active(id = old.id, editId = old.id, start = old.start, end = old.end, title = old.title, exercises = old.exercises.map { it.copy(sets = it.sets.map { s -> s.copy(done = true) }) }) }
        st.saveWorkout(finish(st))
        assertEquals(1, st.data.workouts.size); assertEquals("Workout updated", st.toast!!.text)
    }

    @Test fun discardingClearsTheSession() {
        val (st, _) = loadedState(Seed().routine(push))
        st.handleAction("start:r_push"); checkSet(st, 0, 0)
        st.discardActive()
        assertNull(st.data.active); assertEquals(0L, st.restEnd); assertTrue(st.stack.isEmpty()); assertNull(st.liveInfo())
    }

    /* ---------- import / export ---------- */

    @Test fun importReadsHevyFilesBackupsAndSkipsRepeats() {
        val (st, p) = loadedState()
        p.filesToPick = listOf("workouts.csv" to HEVY_WORKOUTS, "measurements.csv" to HEVY_MEASURES, "notes.txt" to "hello")
        st.importFiles()
        waitUntil("import") { st.importStatus.startsWith("Done") }
        assertTrue(st.importStatus, st.importStatus.contains("2 new workouts, 1 new exercise"))
        assertTrue(st.importStatus, st.importStatus.contains("2 new measurement entries"))
        assertTrue(st.importStatus, st.importStatus.contains("notes.txt: not a Hevy export"))
        assertEquals(2, st.data.workouts.size); assertEquals(2, st.data.measures.size)

        st.importStatus = ""
        p.filesToPick = listOf("workouts.csv" to HEVY_WORKOUTS)
        st.importFiles()
        waitUntil("second import") { st.importStatus.startsWith("Done") }
        assertTrue(st.importStatus, st.importStatus.contains("no new workouts (2 already in Lift Log)"))
        assertEquals(2, st.data.workouts.size)
    }

    @Test fun filesSharedFromHevyImportAndOpenTheGuideWithASummary() {
        val (st, _) = loadedState()
        st.importIncoming(listOf("workouts.csv" to HEVY_WORKOUTS, "measurements.csv" to HEVY_MEASURES))
        waitUntil("shared import") { st.lastImport != null }
        assertEquals(Screen.HevyGuide, st.stack.last())
        val r = st.lastImport!!
        assertEquals(2, r.workouts); assertEquals(2, r.measures); assertEquals(1, r.exercises); assertEquals(0, r.skipped)
        st.importIncoming(listOf("workouts.csv" to HEVY_WORKOUTS))
        waitUntil("second shared import") { st.lastImport!!.workouts == 0 }
        assertEquals(2, st.lastImport!!.skipped)
        assertEquals(1, st.stack.size) // the guide isn't stacked twice
    }

    @Test fun backupRestoresEverything() {
        val (src, _) = loadedState(Seed().routine(push).workout(lastBench()).measure(Measure(id = "m1", date = now, v = mapOf("weight_kg" to 80.0))).settings(Settings(goal = 3, unit = "lb")))
        val json = app.liftlog.logic.backupJson(src.data)
        val (st, p) = loadedState()
        p.filesToPick = listOf("backup.json" to json)
        st.importFiles()
        waitUntil("restore") { st.importStatus.isNotEmpty() && !st.importStatus.startsWith("Importing") }
        assertTrue(st.importStatus, st.importStatus.startsWith("Done") && st.importStatus.contains("items from backup"))
        assertEquals(1, st.data.workouts.size); assertEquals(1, st.data.routines.size); assertEquals(1, st.data.measures.size)
        assertEquals("lb", st.data.settings.unit); assertEquals(3, st.data.settings.goal)
    }

    @Test fun exportSavesFilesOrSaysThereIsNothing() {
        val (empty, ep) = loadedState()
        empty.export("w"); waitUntil("toast") { empty.toast != null }
        assertEquals("No workouts to export yet", empty.toast!!.text); assertTrue(ep.saved.isEmpty())

        val (st, p) = loadedState(Seed().workout(lastBench()).measure(Measure(id = "m1", date = now, v = mapOf("weight_kg" to 80.0))))
        st.export("w"); waitUntil("workouts csv") { p.saved.size == 1 }
        assertTrue(p.saved[0].first.startsWith("liftlog-workouts-")); assertEquals("text/csv", p.saved[0].second)
        assertTrue(p.saved[0].third.contains("\"Bench Press (Barbell)\""))
        st.export("m"); waitUntil("measurements csv") { p.saved.size == 2 }
        assertTrue(p.saved[1].third.lines()[1].contains("80"))
        st.export("b"); waitUntil("backup") { p.saved.size == 3 }
        assertEquals("application/json", p.saved[2].second)
        waitUntil("toast") { st.toast?.text == "Exported" }
    }

    /* ---------- other actions ---------- */

    @Test fun routinesAreBuiltFromRepeatedWorkoutsOnce() {
        val seed = Seed()
        (1..3).forEach { i -> seed.workout(lastBench().copy(id = "w$i", start = now - i * DAY, end = now - i * DAY + 3_600_000, routineId = null)) }
        val (st, _) = loadedState(seed)
        assertEquals(1, st.buildRoutines())
        assertEquals("Push", st.data.routines.values.single().name)
        assertEquals(0, st.buildRoutines())
        assertTrue(st.toast!!.text.startsWith("No new routines"))
    }

    @Test fun sharingHandsTheCardToThePhone() {
        val w = lastBench()
        val (st, p) = loadedState(Seed().workout(w))
        st.shareWorkout(w, dark = true)
        assertEquals("Push", p.shared.single().title)
        p.shareResult = false
        st.shareWorkout(w, dark = true)
        assertEquals("Sharing isn't available here", st.toast!!.text)
    }

    @Test fun pinnedNotesSaveAndClear() {
        val (st, _) = loadedState()
        st.savePin(BENCH, "  Pause at the chest  ")
        assertEquals("Pause at the chest", st.idx.pin(BENCH))
        st.savePin(BENCH, " ")
        assertEquals("", st.idx.pin(BENCH))
    }
}
