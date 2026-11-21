package app.liftlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.liftlog.ui.App

class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidPlatform

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
