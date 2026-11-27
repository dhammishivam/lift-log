package app.liftlog

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    /** Taps on the workout notification and the home screen widget. Shared files come in through ImportActivity. */
    private fun handleIntent(i: Intent?) {
        // A routine link: liftlog://r?d=… from the share page, or the https link itself.
        val link = i?.data?.takeIf { i.action == Intent.ACTION_VIEW && (it.scheme == "liftlog" || it.path.orEmpty().startsWith("/lift-log/r")) }
        if (link != null) { i.data = null; LiveBridge.send("route:$link"); return }
        val a = i?.getStringExtra(LiveBridge.EXTRA) ?: return
        i.removeExtra(LiveBridge.EXTRA)
        LiveBridge.send(a)
    }

    override fun onDestroy() {
        LiveBridge.detach()
        // Swiped away mid-workout: nothing is left to answer the notification's buttons.
        if (isFinishing) LiveNotifier.orphan(applicationContext)
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
