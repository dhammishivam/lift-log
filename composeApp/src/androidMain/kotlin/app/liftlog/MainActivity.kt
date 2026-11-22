package app.liftlog

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
