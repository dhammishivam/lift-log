package app.liftlog

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.liftlog.data.Incoming
import app.liftlog.ui.App

class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidPlatform

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark is the default theme; SystemBars() switches the bar icons if the user picks light.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        platform = AndroidPlatform(this)
        setContent { App(platform) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Taps on the workout notification and the home screen widget, and files shared or opened into the app. */
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        i.getStringExtra(LiveBridge.EXTRA)?.let { a -> i.removeExtra(LiveBridge.EXTRA); LiveBridge.send(a); return }
        val uris: List<Uri> = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(stream(i))
            Intent.ACTION_SEND_MULTIPLE -> streams(i)
            Intent.ACTION_VIEW -> listOfNotNull(i.data)
            else -> emptyList()
        }
        val text = if (uris.isEmpty() && i.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT) else null
        if (uris.isEmpty() && text == null) return
        setIntent(Intent(this, MainActivity::class.java)) // don't import again after a rotation
        // Read on a background thread: an export can be several MB. The read permission lasts while this activity is alive.
        Thread {
            val files = uris.mapNotNull { u -> runCatching { nameOf(u) to contentResolver.openInputStream(u)!!.use { it.readBytes().decodeToString() } }.getOrNull() } +
                listOfNotNull(text?.let { "shared text" to it })
            Incoming.offer(files)
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()

    private fun nameOf(u: Uri): String = runCatching {
        contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: u.lastPathSegment ?: "file"

    override fun onDestroy() {
        LiveBridge.detach()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        platform.onForeground()
    }

    override fun onStop() {
        platform.onBackground()
        super.onStop()
    }
}
