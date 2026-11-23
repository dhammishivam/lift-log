package app.liftlog

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import app.liftlog.logic.nowMs
import app.liftlog.model.*
import app.liftlog.ui.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the real screens like a user: taps, typing, and what appears. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h731dp-xxhdpi")
class UiFlowsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val timing = object : org.junit.rules.TestWatcher() {
        var t0 = 0L
        override fun starting(d: org.junit.runner.Description) { t0 = System.currentTimeMillis() }
        override fun finished(d: org.junit.runner.Description) { println("UI ${d.methodName}: ${System.currentTimeMillis() - t0} ms") }
    }
    private val now = nowMs()
    private val push = Routine(id = "r_push", name = "Push", created = 1, exercises = listOf(bench(listOf(WSet(kg = 60.0, reps = 8.0), WSet(kg = 60.0, reps = 8.0)))))
    private val lastPush = Workout(id = "w_old", title = "Push", start = now - 3 * DAY, end = now - 3 * DAY + 3_600_000, routineId = "r_push", exercises = listOf(bench(listOf(WSet(kg = 60.0, reps = 8.0)))))

    /* ---------- harness ---------- */

    /** A stuck test fails with a stack trace instead of hanging the build. (JUnit's Timeout rule
     *  moves the test off the main thread, which Robolectric refuses, so the limit is checked in pump.) */
    private var deadline = System.currentTimeMillis() + 210_000
    private var launchMs = -1L

    private fun launch(seed: Seed = Seed()): FakePlatform {
        val p = FakePlatform(seed.rows)
        val t0 = System.currentTimeMillis()
        rule.mainClock.autoAdvance = false // the app's clock ticks forever, so the UI never idles
        rule.setContent { App(p) }
        // Wait for the database to load: the tab bar appears with the data. The first test in a JVM
        // pays for Robolectric's and Skia's cold start, so this waits on wall-clock time, not frames.
        while (!has("Exercises") && System.currentTimeMillis() - t0 < 120_000) { rule.mainClock.advanceTimeBy(50); Thread.sleep(50) }
        launchMs = System.currentTimeMillis() - t0
        check(has("Exercises")) { "the app never finished loading ($launchMs ms)\n${screen()}" }
        deadline = System.currentTimeMillis() + 90_000
        pump(300)
        return p
    }
    private fun pump(ms: Long = 600) { var t = 0L; while (t < ms) { check(System.currentTimeMillis() < deadline) { "test took over 90 s after launch (launch took $launchMs ms)\n${screen()}" }; rule.mainClock.advanceTimeBy(50); Thread.sleep(5); t += 50 } }
    /** What is on screen, for failure messages. */
    private fun screen() = runCatching { rule.onAllNodes(androidx.compose.ui.test.isRoot())[0].printToString(maxDepth = 30) }
        .getOrElse { "(no tree: $it)" }.lines().filter { "Text = " in it || "ContentDescription" in it }.joinToString("\n").take(3000)
    private fun nodes(text: String, sub: Boolean = false) = rule.onAllNodesWithText(text, substring = sub, useUnmergedTree = true)
    private fun has(text: String, sub: Boolean = false) = nodes(text, sub).fetchSemanticsNodes().isNotEmpty()
    private fun see(text: String, sub: Boolean = false) {
        repeat(100) { if (has(text, sub)) return; pump(100) }
        error("'$text' never appeared\n${screen()}")
    }
    private fun gone(text: String) { repeat(40) { if (!has(text)) return; pump(100) }; error("'$text' is still showing") }
    private fun click(n: SemanticsNodeInteraction) { runCatching { n.performScrollTo() }; n.performClick(); pump() }
    private fun tap(text: String, last: Boolean = false, sub: Boolean = false) { see(text, sub); click(if (last) nodes(text, sub).onLast() else nodes(text, sub).onFirst()) }
    private fun tapDesc(desc: String) {
        for (i in 0 until 60) { if (rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()) break; pump(100) }
        click(rule.onAllNodesWithContentDescription(desc).onFirst())
    }
    private fun type(field: Int, text: String) {
        val n = rule.onAllNodes(hasSetTextAction())[field]
        runCatching { n.performScrollTo() }; n.performTextClearance(); n.performTextInput(text); pump(300)
    }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; pump() }

    /* ---------- home ---------- */

    @Test fun emptyHomeOffersAProgramOrYourOwnRoutine() {
        val p = launch()
        see("Use a program"); see("Build your own"); see("Coming from Hevy?")
        assertTrue(!has("Build from my workouts"))
        assertTrue("widget gets data on start", p.widgets.isNotEmpty())
        assertNull(p.widgets.last().activeStart)
    }

    @Test fun buildYourOwnOpensTheRoutineEditor() {
        launch()
        tap("Build your own")
        see("New routine"); see("Save")
    }

    /* ---------- programs ---------- */

    @Test fun programFinderSuggestsAddsAndStartsAProgram() {
        val p = launch()
        tap("Use a program")
        see("About you")
        tap("Next"); see("Pick how long you've been lifting")
        tap("New to lifting"); tap("Full gym"); tap("Build muscle")
        tap("Next")
        see("How many days a week can you train?")
        tap("3 days a week")
        see("Best match"); see("Full Body 3-Day")
        tap("Full Body 3-Day")
        see("Full Body A"); see("Add program")
        tap("Add program")
        see("Full Body 3-Day added")
        tap("Start Full Body A now")
        see("Log workout"); see("Squat (Barbell)")
        pump(800)
        assertEquals("Full Body A", p.live.last()!!.title)
    }

    @Test fun addedProgramShowsInAFolderAndSetsTheGoal() {
        launch()
        tap("Use a program")
        tap("6 months to 2 years"); tap("Dumbbells only"); tap("Next")
        tap("4 days a week")
        see("Dumbbell Upper / Lower 4-Day")
        assertTrue("no barbell programs for dumbbells only", !has("Upper / Lower 4-Day"))
        tap("Dumbbell Upper / Lower 4-Day")
        tap("Add program"); tap("Later")
        see("DB Upper A"); see("4 more to hit your goal")
        tap("Programs") // the button next to New routine once you have routines
        see("About you")
    }

    /* ---------- logging a workout ---------- */

    @Test fun loggingAWorkoutWithARecordEndToEnd() {
        val p = launch(Seed().routine(push).workout(lastPush))
        tap("Start routine")
        see("Log workout")
        assertNotNull("notification while training", p.live.last())
        tapDesc("Complete set")
        see("New record!") // the hint suggests 62.5 kg: heavier than last time
        tap("New record!"); gone("New record!")
        assertNotNull(p.live.last()!!.restEnd)
        tap("Finish")
        see("Save workout"); see("3 new records")
        tap("Save workout", last = true)
        see("3 new records!") // Champ celebrates the save
        tap("3 new records!")
        see("Share")
        pump(600)
        assertNull("notification cleared after saving", p.live.last())
        tap("Share")
        assertEquals("Push", p.shared.single().title)
        back()
        see("Profile"); see("Dashboard")
    }

    @Test fun warmUpButtonAddsARampToTheWorkingWeight() {
        launch(Seed().routine(push))
        tap("Start routine")
        tap("Warm-up")
        see("4 warm-up sets added", sub = true)
        assertTrue(has("20×10, 30×8, 42.5×5, 50×3", sub = true))
    }

    @Test fun discardingAWorkoutClearsTheNotification() {
        val p = launch(Seed().routine(push))
        tap("Start routine")
        tap("Discard workout")
        tap("Discard workout", last = true)
        see("Start routine")
        pump(600)
        assertNull(p.live.last())
    }

    @Test fun notificationButtonsReachTheApp() {
        val p = launch(Seed().routine(push))
        rule.runOnUiThread { p.handler!!("start:r_push") }; pump()
        see("Log workout")
        rule.runOnUiThread { p.handler!!("done") }; pump(800)
        assertEquals(1, p.live.last()!!.done)
        rule.runOnUiThread { p.handler!!("skip") }; pump(800)
        assertNull(p.live.last()!!.restEnd)
    }

    /* ---------- body ---------- */

    @Test fun measurementsAreCheckedAndSaved() {
        launch()
        tap("Body"); tap("Log measurements")
        see("Waist (cm)")
        type(1, "78.5"); type(2, "18232")
        tap("Save")
        see("Body fat looks wrong. Check the number.")
        type(2, "18")
        tap("Save")
        see("Measurements saved"); see("78.5 kg")
    }

    @Test fun poundsSwitchTapeMeasuresToInches() {
        // Starts in lb rather than tapping the switch: under Robolectric that tap stalls the frame clock
        // (the switch itself works on the phone, and the Settings tests cover the screen).
        launch(Seed().settings(Settings(unit = "lb")))
        tap("Body"); tap("Log measurements")
        see("Weight (lb)"); see("Waist (in)")
    }

    /* ---------- other screens ---------- */

    @Test fun profileShowsTotalsDashboardAndMusclesWorked() {
        launch(Seed().workout(lastPush))
        tap("Profile"); see("Add your name"); see("Records"); see("Push")
        tap("Add your name"); type(0, "Shivam"); tap("Save"); see("Shivam")
        tap("Muscles"); see("Working sets in the last 30 days", sub = true); back()
        tap("Push"); see("Muscles worked"); see("Chest")
    }

    @Test fun historyAndExerciseRecords() {
        launch(Seed().workout(lastPush))
        tap("Profile"); see("Push"); see("60 kg × 8")
        tap("Exercises"); tap("Bench Press (Barbell)")
        see("Personal records"); see("Heaviest weight")
    }

    @Test fun settingsThemeAndHevyGuide() {
        launch()
        tapDesc("Settings")
        tap("Light"); tap("System"); tap("Dark")
        tap("Start") // Move from Hevy
        see("Export your data from Hevy")
    }

    @Test fun calculatorsOpen() {
        launch()
        tap("Plate calculator"); back()
        tap("1RM calculator"); back()
        see("Start empty workout")
    }
}
