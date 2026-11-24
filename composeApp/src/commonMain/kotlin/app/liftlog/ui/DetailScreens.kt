package app.liftlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.*
import app.liftlog.model.*

/* ---------- workout detail ---------- */
@Composable
fun WorkoutDetailScreen(st: AppState, s: Screen.WorkoutDetail) {
    val idx = st.idx; val lb = LocalLb.current; val p = LocalPal.current
    val w = st.data.workouts[s.id]
    Column(Modifier.fillMaxSize()) {
        TopBar("Workout", left = { IconBtn(Ic.left, "Back", { st.close(s) }) }, right = { if (w != null) IconBtn(Ic.dots, "Workout options", { workoutMenu(st, s, w) }) else Spacer(Modifier.width(44.dp)) })
        if (w == null) { Body { Mu("This workout was deleted.") }; return@Column }
        val pm = idx.prm[w.id] ?: PrMarks(0, emptyMap()); val ss = ssInfo(w.exercises); val cmp = compareWeight(idx.wVol(w))
        Body {
            if (s.fresh) DoneHero(st, w)
            Txt(w.title, size = 24.sp, weight = FontWeight.SemiBold)
            Mu("${dLong(w.start)} · ${tShort(w.start)}", size = 15.sp)
            StatsCard(listOf("Time" to dur(w.end - w.start), "Volume" to wvShort(idx.wVol(w), lb), "Sets" to idx.wSets(w).toString(), "Records" to pm.n.toString()),
                Modifier.padding(vertical = 12.dp))
            if (cmp.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Ic.dumb, null, Modifier.size(18.dp), tint = p.mu); Spacer(Modifier.width(8.dp)); Mu(cmp, size = 15.sp) }
            val recs = workoutRecords(idx, w, lb)
            if (recs.isNotEmpty()) RecordList(recs)
            Btn("Share", { st.shareWorkout(w, p.dark) }, Modifier.padding(top = 10.dp), small = true, icon = Ic.share)
            MusclesWorked(regionSetsOf(w.exercises, { idx.exOf(it) }))
            if (!w.notes.isNullOrBlank()) Txt(w.notes, Modifier.padding(top = 10.dp), size = 15.sp)
            w.exercises.forEachIndexed { ei, it2 ->
                val x = idx.exOf(it2.eid); val q = ss(it2.ss)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    if (q != null) Txt("Superset ${q.first}", size = 13.sp, weight = FontWeight.SemiBold, color = Color(q.second))
                    Txt(x.name, Modifier.clickable { st.push(Screen.ExerciseDetail(x.id)) }.padding(vertical = 4.dp), size = 17.sp, weight = FontWeight.SemiBold, color = p.ac)
                    if (!it2.note.isNullOrBlank()) Mu(it2.note, size = 15.sp)
                    it2.sets.forEachIndexed { si, set ->
                        val marks = pm.m["$ei:$si"]
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (si % 2 == 0) p.s1 else Color.Transparent).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt(setLabel(it2.sets, si), Modifier.width(32.dp), size = 16.sp, weight = FontWeight.Bold, color = when (set.t) { "w" -> p.wu; "d" -> p.dr; "f" -> p.fa; else -> p.mu })
                            Txt(fmtSet(x.type, set, lb), Modifier.weight(1f), size = 16.sp, maxLines = 1)
                            if (marks != null) { Icon(Ic.trophy, null, Modifier.size(15.dp), tint = p.pr); Txt(" " + marks.joinToString(" · ") { PRS[it] ?: it }, size = 13.sp, weight = FontWeight.SemiBold, color = p.pr, maxLines = 1) }
                        }
                    }
                }
                Divider()
            }
        }
    }
}

private fun workoutMenu(st: AppState, s: Screen.WorkoutDetail, w: Workout) {
    st.showMenu(w.title, listOf(
        MenuItem("Share as image") { st.shareWorkout(w, st.data.settings.theme != "light") },
        MenuItem("Edit workout") { st.startWorkout { Active(id = w.id, editId = w.id, start = w.start, end = w.end, title = w.title, notes = w.notes, routineId = w.routineId, exercises = w.exercises.map { it.copy(sets = it.sets.map { s2 -> s2.copy(done = true) }) }) } },
        MenuItem("Repeat workout") { st.startWorkout { st.fromTemplate(w.title, w.routineId, w.exercises) } },
        MenuItem("Save as routine") { st.push(Screen.RoutineEditor(Routine(id = "r_" + rid(), name = w.title, exercises = w.exercises.map { it.copy(note = "", sets = it.sets.map { q -> WSet(t = q.t, kg = q.kg, reps = q.reps, km = q.km, sec = q.sec) }) }), true)) },
        MenuItem("Delete workout", danger = true) {
            st.confirm("Delete this workout?", "It is removed from your history and records.", "Delete workout") {
                st.store.deleteWorkout(w.id); st.close(s); st.say("Workout deleted") { st.store.putWorkout(w) }
            }
        },
    ))
}

/* ---------- exercises tab ---------- */
@Composable
fun ExercisesTab(st: AppState) {
    val idx = st.idx; val q = st.exq.trim().lowercase()
    val all = searchExercises(idx.ex.values.filter { st.exg == "All" || (GROUP[it.muscle] ?: "Other") == st.exg }, q)
    val done = all.filter { idx.h[it.id] != null }.let { d -> if (q.isEmpty()) d.sortedByDescending { idx.h[it.id]!![0].start } else d }
    val rest = all.filter { idx.h[it.id] == null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item { SearchBox(st.exq, "Search exercises") { st.exq = it } }
        item { ChipRow { CHIPS.forEach { c -> Chip(c, st.exg == c) { st.exg = c } } } }
        if (all.isEmpty()) item { Mu(if (st.exq.isBlank()) "No ${st.exg.lowercase()} exercises." else "No exercise matches “${st.exq}”.", Modifier.padding(12.dp)) }
        if (done.isNotEmpty()) { item { Label("Performed") }; items(done, key = { "d" + it.id }) { x -> ExRow(st, x, false) { st.push(Screen.ExerciseDetail(x.id)) } } }
        if (rest.isNotEmpty()) { item { Label(if (done.isNotEmpty()) "Not yet performed" else "All exercises") }; items(rest, key = { "r" + it.id }) { x -> ExRow(st, x, false) { st.push(Screen.ExerciseDetail(x.id)) } } }
        item { Btn("Create custom exercise", { st.push(Screen.ExEditor(null)) }, Modifier.fillMaxWidth().padding(top = 14.dp), icon = Ic.plus) }
    }
}

@Composable
fun SearchBox(value: String, ph: String, onChange: (String) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(p.s1).padding(horizontal = 12.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.search, null, Modifier.size(20.dp), tint = p.mu); Spacer(Modifier.width(8.dp))
        BasicTextField(value, onChange, Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = p.ink, fontSize = 16.sp), cursorBrush = SolidColor(p.ac),
            decorationBox = { inner -> Box { if (value.isEmpty()) Mu(ph, size = 16.sp); inner() } })
    }
}


@Composable
fun ExRow(st: AppState, x: Exercise, sel: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current; val h = st.idx.h[x.id]
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (sel) p.ac else p.s2), contentAlignment = Alignment.Center) {
            if (sel) Icon(Ic.check, null, Modifier.size(20.dp), tint = Color.White) else MuscleThumb(x, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(x.name, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
            Mu("${x.muscle} · ${x.equip}${if (x.custom) " · custom" else ""}", size = 13.sp)
        }
        if (h != null) Column(horizontalAlignment = Alignment.End) { Mu("${h.size} session${if (h.size > 1) "s" else ""}", size = 13.sp); Mu(dShort(h[0].start), size = 13.sp) }
    }
    Divider()
}

/* ---------- exercise detail ---------- */
data class Metric(val k: String, val l: String, val wt: Boolean, val f: (List<WSet>) -> Double, val fmt: (Double) -> String)

fun metricsFor(type: String): List<Metric> {
    fun mx(s: List<WSet>, k: String) = working(s).maxOfOrNull { setM(it)[k] } ?: 0.0
    fun sm(s: List<WSet>, k: String) = s.sumOf { setM(it)[k] }
    val reps: (Double) -> String = { "${fmtN(it)} reps" }; val sec: (Double) -> String = { fmtSec(it) }; val km: (Double) -> String = { "${fmtN(it)} km" }
    return when (type) {
        "bw" -> listOf(Metric("reps", "Most reps", false, { mx(it, "reps") }, reps), Metric("sreps", "Session reps", false, { sm(it, "reps") }, reps))
        "dur" -> listOf(Metric("sec", "Longest set", false, { mx(it, "sec") }, sec), Metric("ssec", "Total time", false, { sm(it, "sec") }, sec))
        "cd" -> listOf(Metric("km", "Distance", false, { sm(it, "km") }, km), Metric("ssec", "Time", false, { sm(it, "sec") }, sec))
        else -> listOf(Metric("w", "Heaviest weight", true, { mx(it, "w") }, { "" }), Metric("orm", "Est. 1RM", true, { mx(it, "orm") }, { "" }),
            Metric("vol", "Best set volume", true, { mx(it, "vol") }, { "" }), Metric("svol", "Session volume", true, { sm(it, "vol") }, { "" }),
            Metric("sreps", "Total reps", false, { sm(it, "reps") }, reps))
    }
}

@Composable
fun ExerciseDetailScreen(st: AppState, s: Screen.ExerciseDetail) {
    val idx = st.idx; val lb = LocalLb.current; val p = LocalPal.current
    val x = idx.exOf(s.id); val t = x.type; val ms = metricsFor(t); val met = ms.firstOrNull { it.k == s.k } ?: ms[0]; val h = idx.h[x.id] ?: emptyList()
    val from = when (s.r) { "3M" -> st.now - 90L * 86_400_000; "1Y" -> st.now - 365L * 86_400_000; else -> 0L }
    val fmt: (Double) -> String = if (met.wt) ({ v -> "${fmtN(r1(v))} ${unitOf(lb)}" }) else met.fmt
    fun ptsFor(hh: List<HEntry>, m: Metric) = hh.filter { it.start >= from }.map { e -> val v = m.f(e.sets); e.start to (if (met.wt) toU(v, lb) else v) }.filter { it.second > 0 }.reversed()
    val pts = ptsFor(h, met)
    val x2 = s.cmp?.let { idx.exOf(it) }; val m2 = x2?.let { metricsFor(it.type).firstOrNull { m -> m.k == met.k } }
    val pts2 = if (x2 != null && m2 != null) ptsFor(idx.h[x2.id] ?: emptyList(), m2) else null
    val cue = idx.cue(x); val pin = idx.pin(x.id)
    Column(Modifier.fillMaxSize()) {
        TopBar(x.name, left = { IconBtn(Ic.left, "Back", { st.close(s) }) }, right = {
            if (x.custom) IconBtn(Ic.dots, "Exercise options", {
                val used = h.size
                st.showMenu(x.name, listOf(
                    MenuItem("Edit exercise") { st.push(Screen.ExEditor(st.data.customs[x.id])) },
                    MenuItem("Delete exercise", danger = true) {
                        if (used > 0) st.say("Used in $used workout${if (used > 1) "s" else ""}, so it can't be deleted")
                        else st.confirm("Delete “${x.name}”?", null, "Delete exercise") { val doc = st.data.customs[x.id]; st.store.deleteCustom(x.id); st.close(s); st.say("Exercise deleted") { doc?.let { st.store.putCustom(it) } } }
                    },
                ))
            }) else Spacer(Modifier.width(44.dp))
        })
        Body {
            Mu("${x.muscle} · ${x.equip} · ${typeOf(t).n}", size = 15.sp)
            if (cue != null) {
                var open by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).card(12.dp).clickable { open = !open }) {
                    Txt(if (open) "Form cues ▾" else "Form cues ▸", size = 15.sp, weight = FontWeight.SemiBold)
                    if (open) Txt(cue, Modifier.padding(top = 6.dp), size = 15.sp)
                }
            }
            if (pin.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(p.acs).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.pin, null, Modifier.size(16.dp), tint = p.ac); Spacer(Modifier.width(8.dp)); Txt(pin, Modifier.weight(1f), size = 14.sp)
                Btn("Edit", { st.push(Screen.PinEditor(x.id, pin)) }, small = true)
            } else Btn("Pin a note", { st.push(Screen.PinEditor(x.id, "")) }, Modifier.padding(top = 8.dp), small = true, icon = Ic.pin)
            if (idx.stalled(x.id)) Row(Modifier.padding(top = 10.dp)) { Icon(Ic.warn, null, Modifier.size(16.dp), tint = p.wu); Spacer(Modifier.width(6.dp)); Txt("No progress in the last 4 weeks compared with the 8 before. A lighter deload week or a variation often restarts progress.", size = 14.sp, color = p.wu) }
            if (h.isEmpty()) { EmptyState(Ic.chart, "No history yet", "Log this exercise once and your progress chart and records appear here."); return@Body }
            val last = pts.lastOrNull()
            Big(last?.let { fmt(it.second) } ?: "—", Modifier.padding(top = 14.dp), size = 34.sp)
            Mu(met.l + (last?.let { " · ${dShort(it.first)}" } ?: ""))
            if (x2 != null) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(p.ac); Txt(" ${x.name}   ", size = 14.sp, maxLines = 1); Dot(Color(0xFFE8930A)); Txt(" ${x2.name}", size = 14.sp, maxLines = 1)
            }
            if (x2 != null && m2 == null) Txt("${x2.name} can't be compared on ${met.l.lowercase()}.", size = 14.sp, color = p.wu)
            LineChart(listOfNotNull(Series(pts, p.ac), pts2?.let { Series(it, Color(0xFFE8930A)) }), Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp))
            ChipRow {
                listOf("3M" to "3 months", "1Y" to "1 year", "All" to "All time").forEach { (k, l) -> Chip(l, s.r == k) { s.r = k } }
                if (s.cmp != null) Chip("Remove comparison", false) { s.cmp = null } else Chip("Compare with…", false) { st.push(Screen.Picker("cmp") { ids -> s.cmp = ids.firstOrNull() }) }
            }
            ChipRow { ms.forEach { m -> Chip(m.l, m === met) { s.k = m.k } } }
            val recKeys = PRKEYS[t] ?: emptyList()
            if (recKeys.isNotEmpty()) {
                H3("Personal records")
                recKeys.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            var b = 0.0; var s0: WSet? = null; var d = 0L
                            h.forEach { e -> working(e.sets).forEach { q -> val v = setM(q)[k]; if (v > b) { b = v; s0 = q; d = e.start } } }
                            Column(Modifier.weight(1f).card(12.dp)) {
                                Mu(PRL[k] ?: k, size = 13.sp)
                                Txt(when (k) { "orm" -> wt(r1(b), lb); "w", "vol" -> wt(b, lb); "sec" -> fmtSec(b); "km" -> "${fmtN(b)} km"; else -> "${fmtN(b)} reps" }, size = 22.sp, weight = FontWeight.Bold, maxLines = 1)
                                s0?.let { q -> Mu("${fmtSet(t, q, lb)} · ${dShort(d)}", size = 13.sp) }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (isVol(t)) {
                val rows = listOf(1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20).mapNotNull { r ->
                    var b = 0.0; var d = 0L
                    h.forEach { e -> working(e.sets).forEach { q -> if ((q.reps ?: 0.0) >= r && (q.kg ?: 0.0) > b) { b = q.kg ?: 0.0; d = e.start } } }
                    if (b > 0) Triple(r, b, d) else null
                }
                if (rows.isNotEmpty()) {
                    H3("Best weight by reps")
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Mu("At least", Modifier.weight(1f), size = 13.sp); Mu("Weight", Modifier.weight(1f), size = 13.sp); Mu("Date", size = 13.sp) }
                    rows.forEach { (r, b, d) -> Divider(); Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Txt("$r rep${if (r > 1) "s" else ""}", Modifier.weight(1f), size = 15.sp); Txt(wt(b, lb), Modifier.weight(1f), size = 15.sp); Mu(dShort(d), size = 15.sp) } }
                }
            }
            H3("History")
            h.forEach { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Txt("${e.w.title} · ${dShort(e.start)}", Modifier.clickable { st.push(Screen.WorkoutDetail(e.wid)) }.padding(vertical = 4.dp), size = 16.sp, weight = FontWeight.SemiBold)
                    e.sets.forEachIndexed { si, q ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Txt(setLabel(e.sets, si), Modifier.width(32.dp), size = 15.sp, weight = FontWeight.Bold, color = when (q.t) { "w" -> p.wu; "d" -> p.dr; "f" -> p.fa; else -> p.mu })
                            Txt(fmtSet(t, q, lb), Modifier.weight(1f), size = 15.sp)
                            if (isVol(t) && (q.reps ?: 0.0) > 0) Mu("1RM ${wt(r1(setM(q).orm), lb)}")
                        }
                    }
                }
                Divider()
            }
        }
    }
}

/* ---------- picker ---------- */
@Composable
fun PickerScreen(st: AppState, s: Screen.Picker) {
    val idx = st.idx; val q = s.q.trim().lowercase()
    fun inG(x: Exercise) = s.g == "All" || (GROUP[x.muscle] ?: "Other") == s.g
    val all = searchExercises(idx.ex.values.filter { inG(it) }, q)
    val rec = if (q.isEmpty()) all.filter { idx.h[it.id] != null }.sortedByDescending { idx.h[it.id]!![0].start }.take(8) else emptyList()
    val rest = all.filter { it !in rec }
    val single = s.mode != "add"
    fun pick(id: String) {
        if (single) { st.close(s); s.onDone(listOf(id)) } else { if (id in s.sel) s.sel.remove(id) else s.sel.add(id) }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(when (s.mode) { "replace" -> "Replace exercise"; "cmp" -> "Compare with"; else -> "Add exercise" },
            left = { IconBtn(Ic.x, "Close", { st.close(s) }) },
            right = { Btn("Create", { st.push(Screen.ExEditor(null, s.q) { id -> pick(id) }) }, small = true) })
        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchBox(s.q, "Search exercise") { s.q = it }
            ChipRow { CHIPS.forEach { c -> Chip(c, s.g == c) { s.g = c } } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (all.isEmpty()) item { Mu("Nothing matches. Tap Create to add “${s.q}” as a custom exercise.", Modifier.padding(vertical = 12.dp)) }
            if (rec.isNotEmpty()) { item { Label("Recent") }; items(rec, key = { "c" + it.id }) { x -> ExRow(st, x, x.id in s.sel) { pick(x.id) } } }
            if (rest.isNotEmpty()) { item { Label(if (s.g == "All") "All exercises" else s.g) }; items(rest, key = { "a" + it.id }) { x -> ExRow(st, x, x.id in s.sel) { pick(x.id) } } }
        }
        if (s.sel.isNotEmpty()) Column(Modifier.padding(16.dp)) {
            Btn("Add ${s.sel.size} exercise${if (s.sel.size > 1) "s" else ""}", { val ids = s.sel.toList(); st.close(s); s.onDone(ids) }, Modifier.fillMaxWidth(), kind = BtnKind.Primary)
        }
    }
}

/* ---------- custom exercise editor ---------- */
@Composable
fun ExEditorScreen(st: AppState, s: Screen.ExEditor) {
    val x = s.existing
    var name by remember { mutableStateOf(x?.name ?: s.prefill) }
    var muscle by remember { mutableStateOf(x?.muscle ?: "Chest") }
    var equip by remember { mutableStateOf(x?.equip ?: "Barbell") }
    var type by remember { mutableStateOf(x?.type ?: "wr") }
    var cue by remember { mutableStateOf(x?.cue ?: "") }
    fun save() {
        val n = name.trim()
        if (n.isEmpty()) { st.say("Give the exercise a name"); return }
        if (x == null && st.idx.ex.values.any { it.name.equals(n, ignoreCase = true) }) { st.say("That exercise already exists"); return }
        val id = x?.id ?: ("x_" + rid())
        st.store.putCustom(CustomEx(id = id, name = n, muscle = muscle, equip = equip, type = type, cue = cue.trim().ifEmpty { null }))
        st.close(s); s.onSaved?.invoke(id)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(if (x != null) "Edit exercise" else "Create exercise", left = { IconBtn(Ic.x, "Close", { st.close(s) }) }, right = { Btn("Save", { save() }, kind = BtnKind.Primary, small = true) })
        Body {
            Field("Name", name, { name = it }, placeholder = "e.g. Landmine Press")
            Mu("Primary muscle", Modifier.padding(top = 8.dp, bottom = 6.dp)); ChipRow { MUSCLES.forEach { m -> Chip(m, muscle == m) { muscle = m } } }
            Mu("Equipment", Modifier.padding(top = 12.dp, bottom = 6.dp)); ChipRow { EQUIP.forEach { m -> Chip(m, equip == m) { equip = m } } }
            Mu("What you log", Modifier.padding(top = 12.dp, bottom = 6.dp)); ChipRow { TYPES.forEach { (k, v) -> Chip(v.n, type == k) { type = k } } }
            Field("Form cues (optional)", cue, { cue = it }, Modifier.padding(top = 8.dp), placeholder = "Your own reminders for this exercise", singleLine = false, minLines = 3)
            Btn("Save exercise", { save() }, Modifier.fillMaxWidth().padding(top = 12.dp), kind = BtnKind.Primary)
        }
    }
}

/* ---------- routine editor ---------- */
@Composable
fun RoutineEditorScreen(st: AppState, s: Screen.RoutineEditor) {
    val p = LocalPal.current
    val r = s.r
    fun save() {
        val name = r.name.trim()
        if (name.isEmpty()) { st.say("Name the routine"); return }
        if (r.exercises.isEmpty()) { st.say("Add at least one exercise"); return }
        st.store.putRoutine(r.copy(name = name, folder = r.folder?.trim()?.ifEmpty { null }, created = r.created ?: nowMs(), updated = nowMs(),
            exercises = r.exercises.map { it.copy(note = it.note ?: "", rest = it.rest ?: 0, sets = it.sets.map { q -> WSet(t = q.t, kg = q.kg, reps = q.reps, km = q.km, sec = q.sec) }) }))
        st.close(s); st.tab = Tab.Workout; st.say("Routine saved")
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(if (s.isNew) "New routine" else "Edit routine", left = { Btn("Cancel", { st.close(s) }, small = true, kind = BtnKind.Ghost) }, right = { Btn("Save", { save() }, kind = BtnKind.Primary, small = true) })
        val ctx = CardCtx(false, s.start, null, null, r.exercises) { l -> s.r = s.r.copy(exercises = l) }
        val ss = ssInfo(r.exercises)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                BasicTextField(r.name, { s.r = s.r.copy(name = it) }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(p.ac),
                    decorationBox = { inner -> Box { if (r.name.isEmpty()) Txt("Routine name", size = 22.sp, weight = FontWeight.SemiBold, color = p.mu); inner() } })
                Divider()
                Field("Folder (optional)", r.folder ?: "", { s.r = s.r.copy(folder = it) }, placeholder = "e.g. Split, Home, Cardio")
                val fs = st.idx.rs.mapNotNull { it.folder }.distinct()
                if (fs.isNotEmpty()) ChipRow { fs.forEach { f -> Chip(f, r.folder == f) { s.r = s.r.copy(folder = f) } } }
                if (r.exercises.isEmpty()) EmptyState(Ic.dumb, "Add the exercises", "Set targets now; they show as hints when you start the routine.")
            }
            items(r.exercises.size) { ei -> ExerciseCard(st, ctx, ei, ss(r.exercises[ei].ss)) }
            item { Btn("Add exercise", { pickToAdd(st, ctx) }, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 40.dp), kind = BtnKind.Primary, icon = Ic.plus) }
        }
    }
}

/* ---------- pinned note ---------- */
@Composable
fun PinEditorScreen(st: AppState, s: Screen.PinEditor) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Pinned note", left = { IconBtn(Ic.x, "Close", { st.close(s) }) }, right = { Btn("Save", { st.savePin(s.eid, s.t); st.close(s); st.say("Note pinned") }, kind = BtnKind.Primary, small = true) })
        Body {
            Mu("${st.idx.exOf(s.eid).name}. Shows every time you log this exercise.", size = 15.sp)
            Field("Note", s.t, { s.t = it }, placeholder = "e.g. Seat on 4, grip the inner handles", singleLine = false, minLines = 3)
            Btn("Save note", { st.savePin(s.eid, s.t); st.close(s); st.say("Note pinned") }, Modifier.fillMaxWidth(), kind = BtnKind.Primary)
            if (st.idx.pin(s.eid).isNotEmpty()) Btn("Remove note", { st.savePin(s.eid, ""); st.close(s); st.say("Note removed") }, Modifier.fillMaxWidth().padding(top = 8.dp), kind = BtnKind.Danger)
        }
    }
}

/** The top of a workout page right after it is saved: which workout this was, how it compares with last time, and the streak. */
@Composable
private fun DoneHero(st: AppState, w: Workout) {
    val idx = st.idx; val p = LocalPal.current
    val n = idx.ws.count { it.start <= w.start }
    val prev = idx.ws.filter { it.id != w.id && it.start < w.start && ((w.routineId != null && it.routineId == w.routineId) || it.title.equals(w.title, true)) }.maxByOrNull { it.start }
    val (streak, _) = idx.weekRuns()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).clip(RoundedCornerShape(20.dp)).background(p.acs).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(p.ac), contentAlignment = Alignment.Center) { Icon(Ic.check, null, Modifier.size(30.dp), tint = Color.White) }
        Txt("Workout complete", Modifier.padding(top = 10.dp), size = 22.sp, weight = FontWeight.Bold)
        Mu("Your ${ordinal(n)} workout${if (streak > 1) " · $streak-week streak" else ""}", size = 15.sp)
        if (prev != null) {
            val dv = idx.wVol(w) - idx.wVol(prev); val ds = idx.wSets(w) - idx.wSets(prev)
            val pct = if (idx.wVol(prev) > 0) kotlin.math.round(dv / idx.wVol(prev) * 100).toInt() else null
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (pct != null && isVolW(idx, w)) Change("Volume", (if (pct >= 0) "+" else "−") + "${kotlin.math.abs(pct)}%", pct >= 0)
                Change("Sets", (if (ds >= 0) "+" else "−") + kotlin.math.abs(ds), ds >= 0)
            }
            Mu("vs ${prev.title}, ${dShort(prev.start)}", Modifier.padding(top = 4.dp), size = 13.sp)
        }
    }
}

private fun isVolW(idx: app.liftlog.logic.Index, w: Workout) = idx.wVol(w) > 0

@Composable
private fun Change(label: String, v: String, up: Boolean) {
    val p = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(v, size = 18.sp, weight = FontWeight.Bold, color = if (up) p.ok else p.fa); Mu(label, size = 12.sp)
    }
}

private fun ordinal(n: Int): String {
    val suf = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "${fmtN(n.toDouble())}$suf"
}
