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
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    // A routine link a friend sent: copied, then pasted here.
    fun fromLink() {
        val t = clip.getText()?.text.orEmpty()
        if (parseRoutineLink(t) == null) st.say("Copy a Lift Log routine link, then tap here") else st.openRoutineLink(t)
    }
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
                if (st.platform.coachEnabled) Btn("Ask the coach", { st.push(Screen.Coach) }, small = true, icon = Ic.chat)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Routines", Modifier.weight(1f))
                if (idx.rs.isNotEmpty()) {
                    IconBtn(Ic.import, "Add routine from a link", { fromLink() }, tint = p.ac, size = 40.dp); Spacer(Modifier.width(4.dp))
                    Btn("Programs", { openPrograms(st) }, small = true, icon = Ic.cal); Spacer(Modifier.width(8.dp))
                    Btn("New routine", { st.push(Screen.RoutineEditor(Routine(id = "r_" + rid(), name = ""), true)) }, small = true, icon = Ic.plus)
                }
            }
        }
        if (idx.rs.isEmpty()) item {
            // Two ways in: a proven program picked for you, or your own routine.
            StartCard(Ic.trophy, "Use a program", "Answer a few questions and pick a proven plan for your week: full body, upper / lower, push / pull / legs and more.", primary = true) { openPrograms(st) }
            StartCard(Ic.plus, "Build your own", "Pick exercises, sets and rest times and save it as a routine.") { st.push(Screen.RoutineEditor(Routine(id = "r_" + rid(), name = ""), true)) }
            if (idx.ws.isEmpty()) StartCard(Ic.import, "Coming from Hevy?", "Bring your workouts, measurements and routines across in a couple of minutes.") { st.push(Screen.HevyGuide) }
            StartCard(Ic.share, "Got a routine link?", "Copy the link a friend sent you, then tap here to add their routine.") { fromLink() }
            if (idx.ws.isNotEmpty()) StartCard(Ic.cal, "Build from my workouts", "Turn workouts you've repeated into routines.") { val n = st.buildRoutines(); if (n > 0) st.say(count(n, "routine") + " added") }
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
        MenuItem("Share as a link") { st.shareRoutine(r) },
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

/** A big tappable choice on the empty Workout tab. */
@Composable
private fun StartCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, primary: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(if (primary) p.acs else p.s1).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (primary) p.ac else p.s2), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (primary) androidx.compose.ui.graphics.Color.White else p.ac)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Txt(title, size = 17.sp, weight = FontWeight.SemiBold); Mu(text, Modifier.padding(top = 2.dp), size = 14.sp) }
        Icon(Ic.right, null, Modifier.size(20.dp), tint = p.mu)
    }
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

/** A routine someone shared: what's in it, then add it (or add and start). */
@Composable
fun SharedRoutineScreen(st: AppState, s: Screen.SharedRoutineView) {
    val idx = st.idx; val sr = s.r; val p = LocalPal.current
    val custom = sr.c.orEmpty().associateBy { it.id }
    Column(Modifier.fillMaxSize()) {
        TopBar("Shared routine", left = { IconBtn(Ic.x, "Close", { st.close(s) }) })
        Body {
            Big(sr.n, size = 26.sp)
            Mu("${count(sr.e.size, "exercise")} · ${count(sr.e.sumOf { it.sets.size }, "set")}", Modifier.padding(top = 4.dp, bottom = 12.dp), size = 15.sp)
            sr.e.forEachIndexed { i, it ->
                val known = idx.ex[it.eid]?.takeIf { _ -> custom[it.eid] == null }
                val name = known?.name ?: custom[it.eid]?.name ?: sr.x.getOrNull(i) ?: "Exercise"
                val type = known?.type ?: custom[it.eid]?.type ?: "wr"
                val work = it.sets.filter { q -> q.t != "w" }
                val first = work.firstOrNull { q -> q.kg != null || q.reps != null || q.sec != null || q.km != null }
                val warm = it.sets.size - work.size
                val line = listOfNotNull(
                    count(work.size, "set"),
                    first?.let { q -> fmtSet(type, q, st.lb) },
                    if (warm > 0) count(warm, "warm-up") else null,
                    it.rest?.takeIf { r -> r > 0 }?.let { r -> "rest ${fmtSec(r.toDouble())}" },
                ).joinToString(" · ")
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).card(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (known != null) { MuscleThumb(known, Modifier.size(40.dp)); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        Txt(name, weight = FontWeight.SemiBold, maxLines = 1)
                        Mu(line, Modifier.padding(top = 2.dp))
                        it.note?.takeIf { n -> n.isNotBlank() }?.let { n -> Mu("“$n”", Modifier.padding(top = 2.dp), size = 13.sp) }
                    }
                }
            }
            if (sr.c.orEmpty().any { c -> idx.ex.values.none { x -> x.name.equals(c.name, true) } })
                Mu("Exercises you don't have yet are added to your list.", Modifier.padding(top = 4.dp), size = 13.sp)
            Btn("Add to my routines", { st.addShared(s, false) }, Modifier.fillMaxWidth().padding(top = 16.dp), kind = BtnKind.Primary)
            Btn("Add and start now", { st.addShared(s, true) }, Modifier.fillMaxWidth().padding(top = 8.dp), icon = Ic.play)
        }
    }
}
