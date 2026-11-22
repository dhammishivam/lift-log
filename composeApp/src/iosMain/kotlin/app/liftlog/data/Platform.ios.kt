package app.liftlog.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

/** iOS uses the swipe-back gesture of the screens themselves; nothing to hook here. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

/** The status bar follows the host view controller; nothing to do here yet. */
@Composable
actual fun SystemBars(dark: Boolean) {}
