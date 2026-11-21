package app.liftlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.*
import app.liftlog.model.*

@Composable
fun WorkoutTab(st: AppState) {
    val idx = st.idx; val p = LocalPal.current
    val ws0 = weekStart(st.now); val wk = idx.ws.filter { it.start >= ws0 }
    val gl = idx.settings.goal ?: 0; val (cur, _) = idx.weekRuns()
    val folders = idx.rs.map { it.folder ?: "" }.distinct().sortedWith(compareBy({ if (it.isEmpty()) 1 else 0 }, { it }))
    val grouped = folders.size > 1 || (folders.firstOrNull() ?: "") != ""
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().card(), verticalAlignment = Alignment.CenterVertically) {
                if (gl > 0) {
                    val frac = (wk.size.toFloat() / gl).coerceAtMost(1f)
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(64.dp)) {
                            val sw = 7.dp.toPx(); val r = Size(size.width - sw, size.height - sw); val o = Offset(sw / 2, sw / 2)
                            drawArc(p.s2, 0f, 360f, false, o, r, style = Stroke(sw))
                            drawArc(p.ac, -90f, 360f * frac, false, o, r, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        Txt("${wk.size}/$gl", size = 17.sp, weight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Txt(when { gl == 0 -> "${wk.size} workout${if (wk.size == 1) "" else "s"} this week"; wk.size >= gl -> "Weekly goal done"; else -> "${gl - wk.size} more to hit your goal" }, size = 16.sp, weight = FontWeight.SemiBold)
                    Mu("${dur(wk.sumOf { it.end - it.start })} trained · $cur-week streak")
                }
                Btn(if (gl > 0) "Edit" else "Set goal", {
                    st.showMenu("Weekly goal", (1..7).map { n -> MenuItem("$n workout${if (n > 1) "s" else ""} a week", on = n == gl) { st.setSettings { it.copy(goal = n) } } } +
                        (if (gl > 0) listOf(MenuItem("No goal") { st.setSettings { it.copy(goal = 0) } }) else emptyList()))
                }, small = true)
            }
        }
        item {
            Label("Quick start")
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(p.s1)
                .clickable { st.startWorkout { Active(id = "w_" + rid(), start = nowMs(), title = "") } }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.plus, null, Modifier.size(22.dp), tint = p.ac); Spacer(Modifier.width(10.dp)); Txt("Start empty workout", weight = FontWeight.SemiBold)
            }
            ChipRowSpaced {
                Btn("Plate calculator", { st.push(Screen.Plate("")) }, small = true)
                Btn("1RM calculator", { st.push(Screen.Orm) }, small = true)
                Btn("Ask the coach", { st.push(Screen.Coach) }, small = true, icon = Ic.chat)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Routines", Modifier.weight(1f))
                Btn("New routine", { st.push(Screen.RoutineEditor(Routine(id = "r_" + rid(), name = ""), true)) }, small = true, icon = Ic.plus)
            }
        }
        if (idx.rs.isEmpty()) item {
            Column(Modifier.fillMaxWidth().card(20.dp)) {
                Txt("No routines yet", weight = FontWeight.SemiBold)
                Mu("Save the workouts you repeat and start them in one tap.", Modifier.padding(top = 6.dp, bottom = 14.dp), size = 15.sp)
                FlowRowX {
                    Btn("Create routine", { st.push(Screen.RoutineEditor(Routine(id = "r_" + rid(), name = ""), true)) }, kind = BtnKind.Primary)
                    if (idx.ws.isNotEmpty()) Btn("Build from my workouts", { val n = st.buildRoutines(); if (n > 0) st.say("$n routines added") })
                    Btn("Add Push / Pull / Legs", { starterRoutines(st) })
                }
            }
        }
        folders.forEach { f ->
            val list = idx.rs.filter { (it.folder ?: "") == f }
            val closed = st.fold[f] == true
            if (grouped) item(key = "f:$f") {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { st.fold[f] = !closed }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.down, null, Modifier.size(20.dp).rotate(if (closed) -90f else 0f), tint = p.ink); Spacer(Modifier.width(8.dp))
                    Txt(f.ifEmpty { "Other routines" }, weight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Mu(list.size.toString())
                }
            }
            if (!closed || !grouped) items(list, key = { it.id }) { r -> RoutineCard(st, r) }
        }
    }
}

@Composable
private fun RoutineCard(st: AppState, r: Routine) {
    val idx = st.idx
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).card(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(r.name, Modifier.weight(1f), size = 18.sp, weight = FontWeight.SemiBold, maxLines = 1)
            IconBtn(Ic.dots, "Options for ${r.name}", { routineMenu(st, r) })
        }
        Mu(r.exercises.joinToString(", ") { idx.exOf(it.eid).name }, Modifier.padding(top = 2.dp, bottom = 12.dp), size = 15.sp)
        Btn("Start routine", { st.startWorkout { st.fromTemplate(r.name, r.id, r.exercises) } }, Modifier.fillMaxWidth(), kind = BtnKind.Primary)
    }
}

private fun routineMenu(st: AppState, r: Routine) {
    val fs = st.idx.rs.mapNotNull { it.folder }.distinct()
    st.showMenu(r.name, listOfNotNull(
        MenuItem("Edit routine") { st.push(Screen.RoutineEditor(r, false)) },
        MenuItem("Duplicate") { st.store.putRoutine(r.copy(id = "r_" + rid(), name = r.name + " (copy)", created = nowMs())) },
        if (fs.isNotEmpty()) MenuItem("Move to folder") {
            st.showMenu("Move to folder", fs.map { f -> MenuItem(f, on = r.folder == f) { st.store.putRoutine(r.copy(folder = f)) } } + MenuItem("No folder") { st.store.putRoutine(r.copy(folder = null)) })
        } else null,
        MenuItem("Delete routine", danger = true) {
            st.confirm("Delete “${r.name}”?", "Past workouts started from it stay in your history.", "Delete routine") {
                st.store.deleteRoutine(r.id); st.say("Routine deleted") { st.store.putRoutine(r) }
            }
        },
    ), if (fs.isEmpty()) "Tip: give a routine a folder name in Edit to group them." else null)
}

fun starterRoutines(st: AppState) {
    fun id(n: String) = "b:" + slug(n)
    fun mk(name: String, list: List<Triple<String, Int, Int>>, c: Int) = Routine(id = "r_" + rid(), name = name, created = nowMs() + c,
        exercises = list.map { (n, reps, rest) -> ExItem(id(n), "", rest, null, List(3) { WSet(reps = reps.toDouble()) }) })
    listOf(
        mk("Push", listOf(Triple("Bench Press (Barbell)", 8, 120), Triple("Overhead Press (Barbell)", 8, 120), Triple("Incline Bench Press (Dumbbell)", 10, 90), Triple("Lateral Raise (Dumbbell)", 12, 60), Triple("Triceps Rope Pushdown", 12, 60)), 0),
        mk("Pull", listOf(Triple("Deadlift (Barbell)", 5, 180), Triple("Pull Up", 8, 120), Triple("Bent Over Row (Barbell)", 8, 90), Triple("Face Pull", 15, 60), Triple("Bicep Curl (Dumbbell)", 12, 60)), 1),
        mk("Legs", listOf(Triple("Squat (Barbell)", 6, 180), Triple("Romanian Deadlift (Barbell)", 8, 120), Triple("Leg Press (Machine)", 10, 90), Triple("Lying Leg Curl (Machine)", 12, 60), Triple("Standing Calf Raise (Machine)", 15, 60)), 2),
    ).forEach { st.store.putRoutine(it) }
    st.say("Push, Pull and Legs added")
}

@Composable
fun ChipRowSpaced(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Wrapping row of buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowX(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
