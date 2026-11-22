package app.liftlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.data.KeepScreenOn
import app.liftlog.data.Platform
import app.liftlog.data.PlatformBackHandler
import app.liftlog.data.Store
import app.liftlog.data.SystemBars
import app.liftlog.logic.clock
import kotlinx.coroutines.delay

@Composable
fun App(platform: Platform) {
    val scope = rememberCoroutineScope()
    val store = remember { Store(platform, scope) }
    val st = remember { AppState(platform, store, scope) }
    LiftTheme(lb = st.lb, theme = st.data.settings.theme) {
        val p = LocalPal.current
        SystemBars(p.dark)
        LaunchedEffect(Unit) { while (true) { st.tick(); delay(250) } }
        val focus = LocalFocusManager.current
        // Close the keyboard whenever the visible screen changes.
        LaunchedEffect(st.stack.size, st.tab) { focus.clearFocus() }
        KeepScreenOn(st.liveOpen() && st.data.settings.awake != false)
        Box(Modifier.fillMaxSize().background(p.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (!store.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Mu("Loading…") }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Big(when (st.tab) { Tab.Workout -> "Workout"; Tab.History -> "History"; Tab.Exercises -> "Exercises"; Tab.Body -> "Measurements" }, Modifier.weight(1f), size = 32.sp)
                        IconBtn(Ic.gear, "Settings", { st.push(Screen.SettingsScreen) })
                    }
                    Box(Modifier.weight(1f)) {
                        when (st.tab) {
                            Tab.Workout -> WorkoutTab(st)
                            Tab.History -> HistoryTab(st)
                            Tab.Exercises -> ExercisesTab(st)
                            Tab.Body -> BodyTab(st)
                        }
                    }
                    ResumePill(st)
                    TabBar(st)
                }
                st.stack.forEach { s -> key(s) { Box(Modifier.fillMaxSize().background(p.bg).noRippleTap {}) { ScreenHost(st, s) } } }
                PlatformBackHandler(st.stack.isNotEmpty() || st.menu != null) {
                    when {
                        st.menu != null -> st.menu = null
                        st.stack.lastOrNull() is Screen.Live -> st.minimize()
                        else -> st.pop()
                    }
                }
                MenuOverlay(st)
                ToastOverlay(st)
            }
        }
    }
}

@Composable
private fun ScreenHost(st: AppState, s: Screen) {
    when (s) {
        is Screen.Live -> LiveScreen(st)
        is Screen.Finish -> FinishScreen(st, s)
        is Screen.WorkoutDetail -> WorkoutDetailScreen(st, s)
        is Screen.ExerciseDetail -> ExerciseDetailScreen(st, s)
        is Screen.Picker -> PickerScreen(st, s)
        is Screen.ExEditor -> ExEditorScreen(st, s)
        is Screen.RoutineEditor -> RoutineEditorScreen(st, s)
        is Screen.MeasureEditor -> MeasureEditorScreen(st, s)
        is Screen.Photos -> PhotosScreen(st, s)
        is Screen.SettingsScreen -> SettingsScreenView(st)
        is Screen.Plate -> PlateScreen(st, s)
        is Screen.Orm -> OrmScreen(st)
        is Screen.Coach -> CoachScreen(st)
        is Screen.PinEditor -> PinEditorScreen(st, s)
        is Screen.HevyGuide -> HevyGuideScreen(st)
    }
}

@Composable
private fun TabBar(st: AppState) {
    val p = LocalPal.current
    Divider()
    Row(Modifier.fillMaxWidth().background(p.bg)) {
        listOf(Triple(Tab.Workout, "Workout", Ic.dumb), Triple(Tab.History, "History", Ic.cal), Triple(Tab.Exercises, "Exercises", Ic.chart), Triple(Tab.Body, "Body", Ic.ruler)).forEach { (t, l, i) ->
            val on = st.tab == t
            Column(Modifier.weight(1f).heightIn(min = 58.dp).clickable { st.tab = t }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(i, null, Modifier.size(22.dp), tint = if (on) p.ac else p.mu)
                Txt(l, size = 12.sp, weight = FontWeight.Medium, color = if (on) p.ac else p.mu)
            }
        }
    }
}

@Composable
private fun ResumePill(st: AppState) {
    val a = st.data.active ?: return
    if (st.liveOpen()) return
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(p.acs).clickable { st.openLive() }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(if (a.editId != null) "Editing workout" else "Workout in progress", Modifier.weight(1f), size = 15.sp, weight = FontWeight.SemiBold)
        if (a.editId == null) Txt(clock(st.now - a.start), Modifier.padding(end = 10.dp), size = 20.sp, weight = FontWeight.Bold, color = p.ac)
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(p.ac).padding(horizontal = 14.dp, vertical = 8.dp)) { Txt("Resume", size = 14.sp, weight = FontWeight.SemiBold, color = Color.White) }
    }
}

@Composable
private fun MenuOverlay(st: AppState) {
    val m = st.menu ?: return
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).noRippleTap { st.menu = null }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 560.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(p.bg).noRippleTap {}
                .padding(horizontal = 12.dp, vertical = 10.dp).navigationBarsPadding(),
        ) {
            if (m.title != null) Txt(m.title, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), size = 17.sp, weight = FontWeight.SemiBold)
            if (m.msg != null) Mu(m.msg, Modifier.padding(horizontal = 10.dp).padding(bottom = 8.dp), size = 15.sp)
            Column(Modifier.heightIn(max = 460.dp).verticalScrollSafe()) {
                m.items.forEach { it2 ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(12.dp)).clickable { st.menu = null; it2.action() }.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Txt(it2.label, size = 16.sp, weight = if (it2.on) FontWeight.SemiBold else FontWeight.Medium, color = when { it2.danger -> p.fa; it2.on -> p.ac; else -> p.ink })
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 50.dp).clip(RoundedCornerShape(12.dp)).background(p.s1).clickable { st.menu = null }, contentAlignment = Alignment.Center) {
                Txt("Cancel", size = 16.sp, color = p.mu)
            }
        }
    }
}

@Composable
private fun ToastOverlay(st: AppState) {
    val t = st.toast ?: return
    val p = LocalPal.current
    LaunchedEffect(t.id) { delay(if (t.undo != null) 6500 else 2800); if (st.toast?.id == t.id) st.toast = null }
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(p.ink).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(t.text, Modifier.weight(1f, fill = false), size = 15.sp, color = p.bg)
            if (t.undo != null) Txt("Undo", Modifier.padding(start = 12.dp).clickable { st.toast = null; t.undo.invoke() }.padding(6.dp), size = 15.sp, weight = FontWeight.Bold, color = p.ac)
        }
    }
}
