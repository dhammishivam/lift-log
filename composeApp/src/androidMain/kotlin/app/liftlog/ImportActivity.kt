package app.liftlog

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import app.liftlog.data.Incoming

/**
 * Receives files shared or opened into Lift Log (Hevy's export, a Lift Log backup). Android may start a share target
 * as a fresh copy inside the sharing app's task, so this invisible activity only reads the files, hands them to the
 * app, opens the one real MainActivity in Lift Log's own task, and closes. MainActivity then imports and shows the result.
 */
class ImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val uris: List<Uri> = when (i?.action) {
            Intent.ACTION_SEND -> listOfNotNull(stream(i))
            Intent.ACTION_SEND_MULTIPLE -> streams(i)
            Intent.ACTION_VIEW -> listOfNotNull(i.data)
            else -> emptyList()
        }
        val text = if (uris.isEmpty() && i?.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT) else null
        if (uris.isEmpty() && text == null) { finish(); return }
        Incoming.busy.value = true
        // Bring Lift Log up straight away so the loading screen shows while the files are read.
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        // Read on a background thread (an export can be several MB). The read permission lasts while this activity lives,
        // so it finishes only after reading.
        Thread {
            val files = uris.mapNotNull { u -> runCatching { nameOf(u) to contentResolver.openInputStream(u)!!.use { it.readBytes().decodeToString() } }.getOrNull() } +
                listOfNotNull(text?.let { "shared text" to it })
            if (files.isEmpty()) Incoming.busy.value = false else Incoming.offer(files)
            runOnUiThread { finish() }
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
}
