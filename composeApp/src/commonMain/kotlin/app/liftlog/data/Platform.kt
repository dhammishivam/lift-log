package app.liftlog.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.cash.sqldelight.db.SqlDriver

/** Everything the shared code needs from the phone. Android implements it now; iOS has a starter implementation. */
interface Platform {
    val name: String
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
    /** When the rest timer ends (null = no rest running). Used to alert while the app is in the background. */
    fun setRestEnd(atMillis: Long?)
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
