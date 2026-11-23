package app.liftlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.count
import app.liftlog.logic.fmtRest
import app.liftlog.logic.rid
import app.liftlog.model.RESTS
import app.liftlog.model.Routine

/** One step of the Move from Hevy guide. [auto] marks it done from the data itself. */
private class Step(val id: String, val title: String, val text: String, val auto: Boolean = false, val autoOnly: Boolean = false)

/** The steps in order; the same list is shown in the Settings summary card. */
private fun steps(st: AppState): List<Step> {
    val idx = st.idx
    val hevyWorkouts = idx.ws.count { it.src == "hevy" }
    return listOf(
        Step("export", "Export your data from Hevy",
            if (st.platform.receivesShares)
                "In Hevy: Profile → Settings (gear icon) → Export & Import Data → Export Data. Tap Export Workouts and pick Lift Log in the list that opens: the import starts by itself and you'll land back here. Then do the same for Export Measurements. If Hevy saves the file instead, open it from Files or Downloads with Lift Log, or use Choose files below."
            else "In the Hevy app: Profile → Settings (gear icon) → Export & Import Data → Export Data. Tap Export Workouts, then Export Measurements, and save both CSV files where this browser can reach them. Do this before your Hevy subscription ends."),
        // Done only once both files are in: workouts and measurements. If someone never logged measurements in Hevy,
        // they can mark it done themselves.
        Step("import", "Import both files here",
            when {
                hevyWorkouts > 0 && idx.ms.isNotEmpty() -> "${count(hevyWorkouts, "Hevy workout")} and ${count(idx.ms.size, "measurement entry", "measurement entries")} are in Lift Log. Importing the same file again won't create duplicates."
                hevyWorkouts > 0 -> "Workouts are in (${count(hevyWorkouts, "Hevy workout")}). Now import the measurements file. If you never logged measurements in Hevy, mark this step as done."
                idx.ms.isNotEmpty() -> "Measurements are in (${count(idx.ms.size, "entry", "entries")}). Now import the workouts file."
                else -> "Pick both files at once. Workouts keep their sets, set types (warm-up, drop, failure), RPE, supersets and notes. Exercises Hevy names differently become custom exercises, and your personal records are worked out from the full history."
            },
            auto = hevyWorkouts > 0 && idx.ms.isNotEmpty(), autoOnly = hevyWorkouts == 0),
        Step("units", "Check your units",
            "Hevy's export is always in kg. Lift Log converts on screen, so pick what you lift in."),
        Step("routines", "Get your routines back",
            if (idx.rs.isNotEmpty()) "You have ${idx.rs.size} routine${if (idx.rs.size == 1) "" else "s"}. Edit them from the Workout tab to set rep targets, rest timers and folders."
            else "Hevy's export doesn't include saved routines. Lift Log rebuilds one for each workout name you used 3+ times in the last 4 months, from its latest session. You can also create them by hand.",
            auto = idx.rs.isNotEmpty(), autoOnly = true),
        Step("prefs", "Match your Hevy settings",
            "Set the default rest timer and a weekly goal. Weight suggestions and keep-screen-on are in Settings too."),
        Step("gaps", "Know what doesn't come over",
            "Progress photos, routine folders, exercise images and anything social stay in Hevy. Re-add photos from the Body tab if you want them here. Everything else you log from now on is stored only on this phone."),
        Step("verify", "Check, back up, then cancel Hevy",
            "Open a recent and an old workout and compare them with Hevy. Then save a full Lift Log backup. Once it all matches, cancel Hevy Pro from your Play Store subscriptions."),
    )
}

/** How many guide steps are done, for the Settings card. */
fun hevyProgress(st: AppState): Pair<Int, Int> {
    val s = steps(st); val done = st.data.settings.hevy.orEmpty()
    return s.count { it.auto || (!it.autoOnly && it.id in done) } to s.size
}

@Composable
fun HevyGuideScreen(st: AppState) {
    val set = st.data.settings; val lb = LocalLb.current
    val all = steps(st); val done = set.hevy.orEmpty()
    fun isDone(s: Step) = s.auto || (!s.autoOnly && s.id in done)
    fun toggle(id: String) = st.setSettings { it.copy(hevy = (it.hevy.orEmpty()).let { l -> if (id in l) l - id else l + id }) }
    val (n, total) = hevyProgress(st)
    Column(Modifier.fillMaxSize()) {
        TopBar("Move from Hevy", left = { IconBtn(Ic.left, "Back", { st.pop() }) })
        Body {
            Mu("$n of $total done. Your Hevy history, PRs and charts carry over; nothing here deletes anything in Hevy.", size = 15.sp)
            Spacer(Modifier.height(8.dp))
            st.lastImport?.let { ImportResult(st, it) }
            all.forEachIndexed { i, s ->
                StepCard(i + 1, s, isDone(s)) {
                    when (s.id) {
                        "export" -> if (st.platform.receivesShares) Btn("Open Hevy", { if (!st.platform.openApp("com.hevy")) st.say("Hevy isn't installed on this phone") }, kind = if (isDone(s)) BtnKind.Plain else BtnKind.Primary)
                        "import" -> {
                            Btn("Choose files", { st.importFiles() }, kind = if (s.auto) BtnKind.Plain else BtnKind.Primary)
                            if (st.importStatus.isNotEmpty()) Mu(st.importStatus, Modifier.padding(top = 8.dp))
                        }
                        "units" -> Seg(listOf("kg" to "kg", "lb" to "lb"), if (lb) "lb" else "kg", { u -> st.setSettings { it.copy(unit = u) } })
                        "routines" -> FlowRowX {
                            Btn("Build from my workouts", {
                                val c = st.buildRoutines()
                                st.say(if (c > 0) "$c routine${if (c > 1) "s" else ""} added" else "No new routines: no workout name was used 3+ times in the last 4 months")
                            }, kind = if (s.auto) BtnKind.Plain else BtnKind.Primary)
                            Btn("Create routine", { st.push(Screen.RoutineEditor(Routine(id = "r_" + rid(), name = ""), true)) })
                        }
                        "prefs" -> FlowRowX {
                            Btn("Rest: ${fmtRest(set.rest)}", { st.showMenu("Default rest timer", RESTS.map { r -> MenuItem(fmtRest(r), on = r == set.rest) { st.setSettings { it.copy(rest = r) } } }) }, icon = Ic.clock)
                            val gl = set.goal ?: 0
                            Btn(if (gl > 0) "Goal: $gl a week" else "Set weekly goal", {
                                st.showMenu("Weekly goal", (1..7).map { g -> MenuItem("$g workout${if (g > 1) "s" else ""} a week", on = g == gl) { st.setSettings { it.copy(goal = g) } } })
                            })
                        }
                        "verify" -> FlowRowX {
                            Btn("Open History", { st.stack.clear(); st.tab = Tab.History })
                            Btn("Save full backup", { st.export("b") })
                        }
                    }
                    if (!s.autoOnly) Btn(if (isDone(s)) "Done" else "Mark as done", { toggle(s.id) }, Modifier.padding(top = 10.dp),
                        small = true, icon = if (isDone(s)) Ic.check else null)
                }
            }
        }
    }
}

@Composable
private fun StepCard(n: Int, s: Step, done: Boolean, actions: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).card(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) p.ok else p.s2), contentAlignment = Alignment.Center) {
                if (done) Icon(Ic.check, "Done", Modifier.size(16.dp), tint = Color.White)
                else Txt("$n", size = 14.sp, weight = FontWeight.SemiBold, color = p.mu)
            }
            Spacer(Modifier.width(10.dp))
            Txt(s.title, Modifier.weight(1f), size = 16.sp, weight = FontWeight.SemiBold, color = if (done) p.mu else p.ink)
        }
        Mu(s.text, Modifier.padding(top = 6.dp, bottom = 10.dp), size = 15.sp)
        actions()
    }
}

/** What the last import brought in, with where to go next. */
@Composable
private fun ImportResult(st: AppState, r: ImportSummary) {
    val p = LocalPal.current
    val lines = buildList {
        if (r.workouts > 0) add(count(r.workouts, "workout"))
        if (r.measures > 0) add(count(r.measures, "measurement entry", "measurement entries"))
        if (r.routines > 0) add(count(r.routines, "routine") + " rebuilt")
        if (r.exercises > 0) add(count(r.exercises, "custom exercise"))
        if (r.backup > 0) add("${r.backup} items from a backup")
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).card(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (lines.isEmpty()) Ic.warn else Ic.check, null, Modifier.size(20.dp), tint = if (lines.isEmpty()) p.mu else p.ok)
            Spacer(Modifier.width(8.dp))
            Txt(if (lines.isEmpty()) "Nothing new imported" else "Imported", size = 17.sp, weight = FontWeight.SemiBold)
        }
        if (lines.isNotEmpty()) Txt(lines.joinToString(" · "), Modifier.padding(top = 6.dp), size = 16.sp)
        if (r.skipped > 0) Mu("${r.skipped} already in Lift Log, so skipped.", Modifier.padding(top = 4.dp), size = 14.sp)
        if (r.unknown.isNotEmpty()) Mu("Not a Hevy export: ${r.unknown.joinToString(", ")}.", Modifier.padding(top = 4.dp), size = 14.sp)
        if (r.workouts > 0 && r.measures == 0 && st.idx.ms.isEmpty()) Mu("Next: export Measurements from Hevy the same way.", Modifier.padding(top = 4.dp), size = 14.sp)
        if (r.workouts > 0 || r.routines > 0) Spacer(Modifier.height(10.dp))
        if (r.workouts > 0 || r.routines > 0) FlowRowX {
            if (r.workouts > 0) Btn("See my history", { st.stack.clear(); st.tab = Tab.History }, kind = BtnKind.Primary, small = true)
            if (st.idx.rs.isNotEmpty()) Btn("See my routines", { st.stack.clear(); st.tab = Tab.Workout }, small = true)
        }
    }
}
