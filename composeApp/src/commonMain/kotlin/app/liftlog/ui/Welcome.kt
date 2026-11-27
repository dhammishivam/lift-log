package app.liftlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.nowMs
import app.liftlog.logic.rid
import app.liftlog.model.Active
import app.liftlog.model.Routine

/** First open with nothing logged yet (and the welcome not seen): show it. */
fun needsWelcome(st: AppState): Boolean =
    st.data.settings.onb != true && st.data.workouts.isEmpty() && st.data.routines.isEmpty() && st.data.active == null

/** One short screen on first open: name, units, workouts a week, and how to start. Everything is optional. */
@Composable
fun WelcomeScreen(st: AppState, s: Screen.Welcome) {
    var name by remember { mutableStateOf(st.data.settings.name.orEmpty()) }
    var unit by remember { mutableStateOf(st.data.settings.unit ?: "kg") }
    var goal by remember { mutableStateOf(st.data.settings.goal ?: 0) }
    fun done(then: () -> Unit) {
        st.setSettings { it.copy(onb = true, name = name.trim().ifBlank { null } ?: it.name, unit = unit, goal = goal.takeIf { g -> g > 0 } ?: it.goal) }
        st.close(s); st.tab = Tab.Workout; then()
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("", right = { Btn("Skip", { done {} }, kind = BtnKind.Ghost, small = true) })
        Body {
            Big("Welcome to Lift Log", size = 28.sp)
            Mu("Log your sets, beat your last session, see every muscle you trained.", Modifier.padding(top = 4.dp, bottom = 8.dp), size = 15.sp)
            Field("Your name (optional)", name, { name = it.take(40) }, placeholder = "Shown on your profile")
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Mu("Weights in", Modifier.weight(1f), size = 15.sp)
                Seg(listOf("kg" to "kg", "lb" to "lb"), unit, { unit = it })
            }
            Mu("Workouts a week", Modifier.padding(top = 16.dp, bottom = 8.dp), size = 15.sp)
            ChipRow { (2..6).forEach { n -> Chip("$n", goal == n) { goal = if (goal == n) 0 else n } } }
            Label("How do you want to start?", Modifier.padding(top = 6.dp))
            StartCard(Ic.trophy, "Pick a program", "A proven plan for your week, matched to you.", primary = true) { done { openPrograms(st) } }
            StartCard(Ic.plus, "Build my own routine", "Choose exercises, sets and rest.") { done { st.push(Screen.RoutineEditor(Routine(id = "r_" + rid(), name = ""), true)) } }
            StartCard(Ic.import, "Import from Hevy", "Bring your history across in a couple of minutes.") { done { st.push(Screen.HevyGuide) } }
            StartCard(Ic.play, "Just start a workout", "Add exercises as you go.") { done { st.startWorkout { Active(id = "w_" + rid(), start = nowMs(), title = "") } } }
        }
    }
}
