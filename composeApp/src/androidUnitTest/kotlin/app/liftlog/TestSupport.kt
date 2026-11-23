package app.liftlog

import androidx.compose.ui.graphics.ImageBitmap
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.liftlog.data.LiveInfo
import app.liftlog.data.Platform
import app.liftlog.data.Store
import app.liftlog.db.LiftDb
import app.liftlog.logic.ShareCard
import app.liftlog.logic.WidgetInfo
import app.liftlog.model.*
import app.liftlog.ui.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A phone stand-in that remembers what the app asked of it, with an in-memory database seeded from [seed]. */
class FakePlatform(private val seed: List<Triple<String, String, String>> = emptyList()) : Platform {
    override val name = "Android"
    override val appVersion = "1.0.0 (test)"
    override val coachEnabled = false
    val opened = mutableListOf<String>()
    /** Files the next import "picks" (name to contents). */
    var filesToPick: List<Pair<String, String>> = emptyList()
    val saved = mutableListOf<Triple<String, String, String>>()
    var saveResult = true
    var alerts = 0; var buzzes = 0
    val restEnds = mutableListOf<Long?>()
    val live = mutableListOf<LiveInfo?>()
    val widgets = mutableListOf<WidgetInfo>()
    val shared = mutableListOf<ShareCard>()
    var shareResult = true
    var handler: ((String) -> Unit)? = null

    override fun openUrl(url: String) { opened += url }
    override fun sqlDriver(): SqlDriver {
        val d = AndroidSqliteDriver(LiftDb.Schema, ApplicationProvider.getApplicationContext(), null)
        val db = LiftDb(d)
        seed.forEach { (id, kind, body) -> db.docQueries.upsert(id, kind, body) }
        return d
    }
    override suspend fun pickTextFiles() = filesToPick
    override suspend fun saveText(fileName: String, mime: String, text: String): Boolean { saved += Triple(fileName, mime, text); return saveResult }
    override suspend fun pickAndStorePhotos() = emptyList<String>()
    override fun loadPhoto(id: String): ImageBitmap? = null
    override fun deletePhoto(id: String) {}
    override fun alert() { alerts++ }
    override fun setRestEnd(atMillis: Long?) { restEnds += atMillis }
    override fun celebrate() { buzzes++ }
    override fun liveWorkout(info: LiveInfo?) { live += info }
    override fun setActionHandler(handler: (String) -> Unit) { this.handler = handler }
    override fun updateWidget(info: WidgetInfo) { widgets += info }
    override fun shareCard(card: ShareCard): Boolean { shared += card; return shareResult }
    override fun mascot(kind: String): ImageBitmap? = null
}

/** Builds database rows for a test. */
class Seed {
    val rows = mutableListOf<Triple<String, String, String>>()
    fun workout(w: Workout) = apply { rows += Triple(w.id, "workout", JsonX.encodeToString(Workout.serializer(), w)) }
    fun routine(r: Routine) = apply { rows += Triple(r.id, "routine", JsonX.encodeToString(Routine.serializer(), r)) }
    fun measure(m: Measure) = apply { rows += Triple(m.id, "measure", JsonX.encodeToString(Measure.serializer(), m)) }
    fun settings(s: Settings) = apply { rows += Triple("settings", "settings", JsonX.encodeToString(Settings.serializer(), s)) }
    fun active(a: Active) = apply { rows += Triple("active", "active", JsonX.encodeToString(Active.serializer(), a)) }
}

/** An [AppState] over a fresh database, loaded and ready (no UI). */
fun loadedState(seed: Seed = Seed(), platform: FakePlatform = FakePlatform(seed.rows)): Pair<AppState, FakePlatform> {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val store = Store(platform, scope)
    val until = System.currentTimeMillis() + 10_000
    while (!store.loaded) { check(System.currentTimeMillis() < until) { "store never loaded" }; Thread.sleep(10) }
    return AppState(platform, store, scope) to platform
}

/** Waits for background work (imports, saves) to finish. */
fun waitUntil(what: String, ms: Long = 5_000, cond: () -> Boolean) {
    val until = System.currentTimeMillis() + ms
    while (!cond()) { check(System.currentTimeMillis() < until) { "timed out waiting for $what" }; Thread.sleep(10) }
}

fun bench(sets: List<WSet>) = ExItem(eid = "b:" + slug("Bench Press (Barbell)"), rest = 120, sets = sets)
fun squat(sets: List<WSet>) = ExItem(eid = "b:" + slug("Squat (Barbell)"), rest = 180, sets = sets)
fun pullUp(sets: List<WSet>) = ExItem(eid = "b:" + slug("Pull Up"), rest = 90, sets = sets)
const val DAY = 86_400_000L
