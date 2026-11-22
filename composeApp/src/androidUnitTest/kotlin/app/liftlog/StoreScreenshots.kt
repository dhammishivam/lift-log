package app.liftlog

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.liftlog.data.Platform
import app.liftlog.db.LiftDb
import app.liftlog.logic.nowMs
import app.liftlog.model.*
import app.liftlog.ui.App
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Renders real app screens with demo data for the Play Store listing (1080 x 1920, dark theme).
 * Output: one PNG per screen in composeApp/build/store-screenshots
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h731dp-xxhdpi")
class StoreScreenshots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val out = File("build/store-screenshots").apply { mkdirs() }

    private class DemoPlatform(private val seed: List<Triple<String, String, String>>) : Platform {
        override val name = "Android"
        override val appVersion = "1.0.0"
        override val coachEnabled = false
        override fun openUrl(url: String) {}
        override fun sqlDriver(): SqlDriver {
            val d = AndroidSqliteDriver(LiftDb.Schema, ApplicationProvider.getApplicationContext(), null)
            val db = LiftDb(d)
            seed.forEach { (id, kind, body) -> db.docQueries.upsert(id, kind, body) }
            return d
        }
        override suspend fun pickTextFiles() = emptyList<Pair<String, String>>()
        override suspend fun saveText(fileName: String, mime: String, text: String) = false
        override suspend fun pickAndStorePhotos() = emptyList<String>()
        override fun loadPhoto(id: String): ImageBitmap? = null
        override fun deletePhoto(id: String) {}
        override fun alert() {}
        override fun setRestEnd(atMillis: Long?) {}
    }

    private fun pump(ms: Long = 800) {
        var t = 0L
        while (t < ms) { rule.mainClock.advanceTimeBy(50); Thread.sleep(10); t += 50 }
    }

    private fun waitForText(text: String) {
        repeat(80) {
            if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            pump(100)
        }
        error("'$text' never appeared")
    }

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; pump() }

    private fun tap(text: String) { waitForText(text); rule.onAllNodesWithText(text).onFirst().performClick(); pump() }

    private fun shot(name: String) {
        pump(600)
        // Draw the window directly: captureToImage() waits for an idle UI, and the app's clock never idles.
        var bmp: Bitmap? = null
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        val bmp = bmp!!
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Saved ${out.path}/$name.png ${bmp.width}x${bmp.height}")
    }

    @Test fun storeScreenshots() {
        rule.mainClock.autoAdvance = false
        rule.setContent { App(DemoPlatform(DemoData.docs())) }
        waitForText("Resume")
        shot("01-workout")
        tap("Resume"); shot("02-live-workout")
        back()
        tap("History"); shot("03-history")
        tap("Exercises"); tap("Bench Press (Barbell)"); shot("04-exercise-progress")
        back()
        tap("Body"); shot("05-measurements")
    }
}

/** Twelve weeks of believable training, routines, weigh-ins and a workout in progress. */
object DemoData {
    private fun id(name: String) = "b:" + slug(name)
    private val day = 86_400_000L

    private data class Lift(val name: String, val start: Double, val step: Double, val reps: List<Int>)
    private val push = listOf(Lift("Bench Press (Barbell)", 70.0, 1.25, listOf(8, 8, 7)), Lift("Overhead Press (Barbell)", 40.0, 0.6, listOf(8, 8, 6)), Lift("Incline Bench Press (Dumbbell)", 24.0, 0.4, listOf(10, 10, 9)), Lift("Triceps Pushdown", 25.0, 0.5, listOf(12, 12, 11)), Lift("Lateral Raise (Dumbbell)", 8.0, 0.2, listOf(15, 14, 12)))
    private val pull = listOf(Lift("Deadlift (Barbell)", 110.0, 2.0, listOf(5, 5, 5)), Lift("Lat Pulldown (Cable)", 55.0, 0.8, listOf(10, 10, 9)), Lift("Bent Over Row (Barbell)", 60.0, 1.0, listOf(8, 8, 8)), Lift("Bicep Curl (Dumbbell)", 12.0, 0.25, listOf(12, 10, 10)))
    private val legs = listOf(Lift("Squat (Barbell)", 90.0, 1.5, listOf(6, 6, 6)), Lift("Romanian Deadlift (Barbell)", 80.0, 1.0, listOf(8, 8, 8)), Lift("Leg Press (Machine)", 160.0, 3.0, listOf(10, 10, 10)), Lift("Lying Leg Curl (Machine)", 40.0, 0.6, listOf(12, 12, 10)))
    private val plans = listOf("Push" to push, "Pull" to pull, "Legs" to legs)

    private fun r25(x: Double) = (x / 2.5).roundToInt() * 2.5

    private fun items(lifts: List<Lift>, session: Int, done: Boolean?) = lifts.map { l ->
        val kg = if (l.start < 30) (l.start + l.step * session).let { (it * 2).roundToInt() / 2.0 } else r25(l.start + l.step * session)
        ExItem(eid = id(l.name), rest = 120, sets = listOf(WSet(t = "w", kg = r25(kg * 0.5), reps = 10.0, done = done)) + l.reps.map { WSet(kg = kg, reps = it.toDouble(), done = done) })
    }

    fun docs(): List<Triple<String, String, String>> {
        val out = mutableListOf<Triple<String, String, String>>()
        val now = nowMs()
        val start = now - 84 * day
        var n = 0
        // Four sessions a week: Mon, Tue, Thu, Sat, rotating Push / Pull / Legs.
        for (week in 0 until 12) for (d in listOf(0, 1, 3, 5)) {
            val t = start + (week * 7 + d) * day + 18 * 3_600_000L
            if (t > now - day) continue
            val (title, lifts) = plans[n % 3]
            val w = Workout(id = "w_demo$n", title = title, start = t, end = t + (55 + n % 4 * 5) * 60_000L, exercises = items(lifts, n / 3, true))
            out += Triple(w.id, "workout", JsonX.encodeToString(Workout.serializer(), w))
            n++
        }
        plans.forEachIndexed { i, (title, lifts) ->
            val r = Routine(id = "r_demo$i", name = title, exercises = items(lifts, n / 3, null))
            out += Triple(r.id, "routine", JsonX.encodeToString(Routine.serializer(), r))
        }
        for (k in 0 until 12) {
            val m = Measure(id = "m_demo$k", date = start + k * 7 * day + 8 * 3_600_000L, v = mapOf("weight_kg" to (82.4 - k * 0.35 + listOf(0.2, -0.1, 0.3, 0.0).let { it[k % 4] }).let { (it * 10).roundToInt() / 10.0 }, "fat_percent" to (19.0 - k * 0.2).let { (it * 10).roundToInt() / 10.0 }))
            out += Triple(m.id, "measure", JsonX.encodeToString(Measure.serializer(), m))
        }
        out += Triple("settings", "settings", JsonX.encodeToString(Settings.serializer(), Settings(goal = 4)))
        // A push session in progress: first two exercises done, the third half way.
        val live = items(push, n / 3 + 1, null).mapIndexed { i, it ->
            it.copy(sets = it.sets.mapIndexed { si, s -> s.copy(done = if (i < 2 || (i == 2 && si < 2)) true else null) })
        }
        val a = Active(id = "a_demo", title = "Push", start = now - 38 * 60_000L, exercises = live)
        out += Triple("active", "active", JsonX.encodeToString(Active.serializer(), a))
        return out
    }
}
