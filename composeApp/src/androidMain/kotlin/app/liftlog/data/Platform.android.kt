package app.liftlog.data

import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun SystemBars(dark: Boolean) {
    val act = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity ?: return
    androidx.compose.runtime.LaunchedEffect(dark) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t)
        act.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
