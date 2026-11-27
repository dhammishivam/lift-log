package app.liftlog.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.cash.sqldelight.db.SqlDriver
import app.liftlog.logic.ShareCard
import app.liftlog.logic.WidgetInfo

/** What the "workout in progress" notification shows. */
data class LiveInfo(
    val title: String,
    val start: Long,
    /** When the rest timer ends, or null when no rest is running. */
    val restEnd: Long?,
    /** "Bench Press (Barbell) · set 2 · 60 kg × 8", or null when every set is ticked. */
    val next: String?,
    val done: Int,
    val total: Int,
    /** Exercise of the next set, e.g. "Bench Press (Barbell)". */
    val exName: String? = null,
    /** The next set on its own: "Set 2 of 4 · 60 kg × 8", "Warm-up · 20 kg × 10". */
    val setLine: String? = null,
    /** Length of the running rest in ms, for the countdown bar. */
    val restTotal: Long? = null,
)

/** Everything the shared code needs from the phone. Android implements it now; iOS has a starter implementation. */
interface Platform {
    val name: String
    /** Where database reads and writes run: one background thread, so writes stay in order.
     *  Tests run them in place, because Robolectric's SQLite stalls when used from another thread. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dbDispatcher: kotlinx.coroutines.CoroutineDispatcher get() = kotlinx.coroutines.Dispatchers.Default.limitedParallelism(1)
    /** App version shown in Settings, e.g. "1.0.0 (12)". */
    val appVersion: String
    /** False in Play builds until the AI coach has an in-app report button. */
    val coachEnabled: Boolean
    /** Opens a web page in the browser. */
    fun openUrl(url: String)
    fun sqlDriver(): SqlDriver

    /** Lets the user pick text files (CSV/JSON). Returns (file name, contents). */
    suspend fun pickTextFiles(): List<Pair<String, String>>
    /** Lets the user save a text file. Returns true if saved. */
    suspend fun saveText(fileName: String, mime: String, text: String): Boolean

    /** Lets the user pick photos, stores downscaled copies and returns their ids. */
    suspend fun pickAndStorePhotos(): List<String>
    fun loadPhoto(id: String): ImageBitmap?
    fun deletePhoto(id: String)

    /** Vibrate and beep (rest over). */
    fun alert()
    /** The rest timer ran out. In the background Android shows a "Rest over" notification instead. */
    fun restOver() = alert()
    /** When the rest timer ends (null = no rest running). Used to alert while the app is in the background. */
    fun setRestEnd(atMillis: Long?)

    /** A short buzz for a new record. */
    fun celebrate() {}
    /** Ongoing notification while a workout runs; null removes it. */
    fun liveWorkout(info: LiveInfo?) {}
    /**
     * Receives taps from the notification and the home screen widget: "minus", "plus", "skip", "done",
     * "open", "empty" or "start:<routine id>". Taps that arrived before this was set are delivered now.
     */
    fun setActionHandler(handler: (String) -> Unit) {}
    /** Refreshes the home screen widget. */
    fun updateWidget(info: WidgetInfo) {}
    /** A mascot picture for celebrations ("titan", "classic", "pump", "champ"), or null. */
    fun mascot(kind: String): ImageBitmap? = null
    /** Opens the share sheet with [text]. False where there is none. */
    fun shareText(text: String, title: String): Boolean = false
    /** Shares a workout image. Returns false where sharing images isn't available. */
    fun shareCard(card: ShareCard): Boolean = false
    /** Other apps can hand their exports straight to Lift Log (Android's share and open-with). */
    val receivesShares: Boolean get() = false
    /** The dev build (com.krishiv.liftlog.dev): shows developer tools such as a test crash for Crashlytics. */
    val devBuild: Boolean get() = false
    /** Turns crash reporting on or off; the choice sticks until changed. No-op where there is none. */
    fun setCrashReports(on: Boolean) {}
    /** Opens another installed app, e.g. Hevy to export from, at [link] if given. False if it isn't installed or this can't be done here. */
    fun openApp(pkg: String, link: String? = null): Boolean = false
}

/** Keeps the screen awake while shown. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** System back button / gesture. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Light or dark status and navigation bar icons to match the app theme. */
@Composable
expect fun SystemBars(dark: Boolean)

/** Files handed to the app from outside (shared from Hevy, or opened from a file manager), waiting for the UI to import them. */
object Incoming {
    val files = kotlinx.coroutines.flow.MutableStateFlow<List<Pair<String, String>>>(emptyList())
    /** True from the moment a share arrives until its files are read, so the app can show the import screen at once. */
    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun offer(f: List<Pair<String, String>>) { if (f.isNotEmpty()) files.value = files.value + f }
    fun take(): List<Pair<String, String>> = files.value.also { files.value = emptyList() }
}
