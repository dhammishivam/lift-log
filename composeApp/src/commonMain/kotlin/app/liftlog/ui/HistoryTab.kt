package app.liftlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import app.liftlog.model.GROUP
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.*
import app.liftlog.model.Workout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.math.roundToInt

@Composable
fun HistoryTab(st: AppState) {
    val idx = st.idx
    if (idx.ws.isEmpty()) {
        Body {
            ProfileHeader(st); Dashboard(st)
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).card(20.dp)) {
                Txt("No workouts yet", weight = FontWeight.SemiBold)
                Mu("Finished workouts land here with their time, volume and records. Coming from Hevy? Bring your whole history across.", Modifier.padding(top = 6.dp, bottom = 14.dp), size = 15.sp)
                FlowRowX { Btn("Import from Hevy", { st.push(Screen.HevyGuide) }, kind = BtnKind.Primary); Btn("Start a workout", { st.tab = Tab.Workout }) }
            }
        }
        return
    }
    HistoryList(st)
}

/** Calendar, Muscles and Statistics, each opened from a Profile tile. */
@Composable
fun StatsScreen(st: AppState, s: Screen.Stats) {
    Column(Modifier.fillMaxSize()) {
        TopBar(when (s.view) { "cal" -> "Calendar"; "mus" -> "Muscles"; else -> "Statistics" }, left = { IconBtn(Ic.left, "Back", { st.pop() }) })
        Body { when (s.view) { "cal" -> CalendarView(st); "mus" -> MusclesView(st); else -> ReportView(st) } }
    }
}

/** Name, avatar and all-time totals at the top of the Profile tab. */
@Composable
private fun ProfileHeader(st: AppState) {
    val p = LocalPal.current; val lb = LocalLb.current
    val name = st.data.settings.name.orEmpty()
    var edit by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(name) }
    val ps = profileStats(st.idx)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(p.ac.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                if (name.isNotBlank()) Txt(name.trim().first().uppercase(), size = 28.sp, weight = FontWeight.Bold, color = p.ac)
                else Icon(Ic.user, null, Modifier.size(30.dp), tint = p.ac)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (edit) Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(null, draft, { draft = it }, Modifier.weight(1f), placeholder = "Your name")
                    Spacer(Modifier.width(8.dp))
                    Btn("Save", { st.setSettings { it.copy(name = draft.trim().ifBlank { null }) }; edit = false }, small = true, kind = BtnKind.Primary)
                } else {
                    Txt(name.ifBlank { "Add your name" }, Modifier.clickable { draft = name; edit = true }, size = 22.sp, weight = FontWeight.SemiBold, color = if (name.isBlank()) p.mu else p.ink)
                    Mu(if (ps.since != null) "Training since ${ldt(ps.since).let { monthLabel(it.year, it.monthNumber) }}" else "Your training, all in one place", size = 14.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Stat("Workouts", ps.workouts.toString(), Modifier.weight(1f))
            Stat("Time", dur(ps.ms), Modifier.weight(1f))
            Stat("Volume", wv(ps.vol, lb), Modifier.weight(1f))
            Stat("Records", ps.records.toString(), Modifier.weight(1f))
        }
    }
}

/** Shortcuts under the Profile header. */
@Composable
private fun Dashboard(st: AppState) {
    val tiles = listOf(
        Triple("Statistics", Ic.chart) { st.push(Screen.Stats("rep")) },
        Triple("Muscles", Ic.dumb) { st.push(Screen.Stats("mus")) },
        Triple("Calendar", Ic.cal) { st.push(Screen.Stats("cal")) },
        Triple("Measures", Ic.ruler) { st.tab = Tab.Body },
    )
    Label("Dashboard")
    tiles.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (label, icon, go) ->
                val p = LocalPal.current
                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.s1).clickable { go() }.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(20.dp), tint = p.ac); Spacer(Modifier.width(10.dp)); Txt(label, size = 16.sp, weight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HistoryList(st: AppState) {
    val idx = st.idx; val lb = LocalLb.current; val p = LocalPal.current
    val w0 = weekStart(st.now); val weeks = (0 until 12).map { addDays(w0, -(11 - it) * 7) }
    fun valOf(w: Workout) = when (st.hm) { "dur" -> (w.end - w.start) / 60000.0; "vol" -> idx.wVol(w); else -> idx.wSets(w).toDouble() }
    val vals = weeks.mapIndexed { i, s -> val e = if (i < 11) weeks[i + 1] else Long.MAX_VALUE; idx.ws.filter { it.start in s until e }.sumOf { valOf(it) } }
    val mx = maxOf(1.0, vals.maxOrNull() ?: 0.0); val v = vals[st.wsel]
    val months = idx.ws.groupBy { val d = ldt(it.start); d.year * 12 + d.monthNumber }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item { ProfileHeader(st); Dashboard(st) }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).card()) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Big(when (st.hm) { "dur" -> dur((v * 60000).toLong()); "vol" -> wv(v, lb); else -> "${v.roundToInt()} sets" }, size = 30.sp)
                        Mu(if (st.wsel == 11) "This week" else "Week of ${dShort(weeks[st.wsel])}")
                    }
                    Seg(listOf("dur" to "Duration", "vol" to "Volume", "sets" to "Sets"), st.hm, { st.hm = it })
                }
                Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    vals.forEachIndexed { i, x ->
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { st.wsel = i }, contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.8f).fillMaxHeight((x / mx).toFloat().coerceAtLeast(0.03f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (i == st.wsel) p.ac else p.ac.copy(alpha = 0.32f)))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { Mu(dShort(weeks[0]), Modifier.weight(1f), size = 13.sp); Mu("This week", size = 13.sp) }
            }
        }
        months.forEach { (_, list) ->
            item { Label(ldt(list[0].start).let { monthLabel(it.year, it.monthNumber) }) }
            items(list, key = { it.id }) { w -> WorkoutCard(st, w) }
        }
    }
}

@Composable
fun WorkoutCard(st: AppState, w: Workout) {
    val idx = st.idx; val lb = LocalLb.current; val p = LocalPal.current
    val pm = idx.prm[w.id]
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(p.s1).clickable { st.push(Screen.WorkoutDetail(w.id)) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Txt(w.title, size = 18.sp, weight = FontWeight.SemiBold, maxLines = 1)
        Mu("${dLong(w.start)} · ${tShort(w.start)}")
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Stat("Time", dur(w.end - w.start)); Stat("Volume", wv(idx.wVol(w), lb))
            if ((pm?.n ?: 0) > 0) Column { Mu("Records", size = 13.sp); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Ic.trophy, null, Modifier.size(16.dp), tint = p.pr); Txt(" ${pm!!.n}", size = 21.sp, weight = FontWeight.SemiBold) } }
        }
        Divider()
        w.exercises.take(4).forEach { it2 ->
            val x = idx.exOf(it2.eid)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Txt("${it2.sets.size} × ${x.name}", Modifier.weight(1f), size = 15.sp, maxLines = 1)
                Mu(bestSet(x.type, it2.sets, lb), Modifier.padding(start = 12.dp), size = 15.sp)
            }
        }
        if (w.exercises.size > 4) Mu("+${w.exercises.size - 4} more exercise${if (w.exercises.size - 4 > 1) "s" else ""}", Modifier.padding(top = 4.dp), size = 15.sp)
    }
}

fun bestSet(type: String, sets: List<app.liftlog.model.WSet>, lb: Boolean): String {
    val pool = working(sets).ifEmpty { sets }; if (pool.isEmpty()) return ""
    val k = when (type) { "cd" -> "km"; "dur" -> "sec"; "bw" -> "reps"; else -> "orm" }
    return fmtSet(type, pool.maxBy { setM(it)[k] }, lb)
}

@Composable
internal fun CalendarView(st: AppState) {
    val idx = st.idx; val p = LocalPal.current
    val today = ldt(st.now).date
    val (y, mo) = addMonths(today.year, today.monthNumber, st.cm)
    val first = LocalDate(y, mo, 1); val next = addMonths(y, mo, 1).let { LocalDate(it.first, it.second, 1) }
    val days = (startOfDay(next) - startOfDay(first)).let { ((it / 86_400_000.0) + 0.5).toInt() }
    val lead = first.dayOfWeek.isoDayNumber - 1
    val inMonth = idx.ws.filter { val d = ldt(it.start); d.year == y && d.monthNumber == mo }
    val byDay = inMonth.groupBy { ldt(it.start).dayOfMonth }
    val (cur, best) = idx.weekRuns()
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Stat("Week streak", cur.toString(), Modifier.weight(1f)); Stat("Longest", "$best wk", Modifier.weight(1f))
        Stat("This month", inMonth.size.toString(), Modifier.weight(1f)); Stat("Time", dur(inMonth.sumOf { it.end - it.start }), Modifier.weight(1f))
    }
    Column(Modifier.fillMaxWidth().card()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.left, "Previous month", { st.cm -= 1 })
            Txt(monthLabel(y, mo), Modifier.weight(1f), size = 17.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
            IconBtn(Ic.right, "Next month", { if (st.cm < 0) st.cm += 1 }, tint = if (st.cm < 0) p.mu else p.mu.copy(alpha = 0.3f))
        }
        Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Txt(it, Modifier.weight(1f).padding(vertical = 4.dp), size = 13.sp, color = p.mu, align = TextAlign.Center) } }
        val cells = List(lead) { 0 } + (1..days).toList()
        cells.chunked(7).forEach { wk ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val d = wk.getOrNull(i) ?: 0
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (d > 0) {
                            val ws = byDay[d]; val isToday = d == today.dayOfMonth && st.cm == 0
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(if (ws != null) p.ac else Color.Transparent)
                                .then(if (isToday) Modifier.background(Color.Transparent) else Modifier)
                                .clickable(enabled = ws != null) {
                                    if (ws!!.size == 1) st.push(Screen.WorkoutDetail(ws[0].id))
                                    else st.showMenu("Workouts that day", ws.map { w -> MenuItem(w.title) { st.push(Screen.WorkoutDetail(w.id)) } })
                                }, contentAlignment = Alignment.Center) {
                                Txt(d.toString(), size = 16.sp, weight = if (isToday) FontWeight.Bold else FontWeight.SemiBold, color = if (ws != null) Color.White else if (isToday) p.ac else p.ink)
                            }
                        }
                    }
                }
            }
        }
    }
    Mu("A week counts toward your streak with at least one workout. Tap a highlighted day to open it.", Modifier.padding(top = 10.dp))
}

@Composable
internal fun MusclesView(st: AppState) {
    val idx = st.idx; val p = LocalPal.current
    val exOf: (String) -> app.liftlog.model.Exercise = { idx.exOf(it) }
    val days = st.mu; val now = st.now; val from = now - days * 86_400_000L; val pf = from - days * 86_400_000L
    val cur = idx.ws.filter { it.start >= from }; val prev = idx.ws.filter { it.start in pf until from }
    val cnt = regionSets(cur, exOf); val pc = regionSets(prev, exOf)
    val wk = days / 7.0; val tot = cnt.values.sum(); val ptot = pc.values.sum()
    var mode by remember { mutableStateOf("vol") }
    var sel by remember { mutableStateOf<String?>(null) }
    val last = remember(idx.ws.size) { lastTrained(idx.ws, exOf) }
    Seg(listOf("vol" to "Volume", "rec" to "Recovery"), mode) { mode = it }
    if (mode == "vol") {
        Spacer(Modifier.height(10.dp)); ChipRow { listOf(7 to "7 days", 30 to "30 days", 90 to "3 months", 365 to "1 year").forEach { (k, l) -> Chip(l, st.mu == k) { st.mu = k } } }
        Big("${setsLabel(tot)} sets", Modifier.padding(top = 12.dp))
        if (tot > 0 || ptot > 0) { val d = tot - ptot
            Txt((if (d >= 0) "+" else "−") + setsLabel(kotlin.math.abs(d)) + " vs previous ${if (days == 365) "year" else "$days days"}", size = 14.sp, color = if (d >= 0) p.ok else p.fa) }
        Mu(if (days > 7) "About ${setsLabel(tot / wk)} sets a week" else "This week", size = 14.sp)
        if (cur.isEmpty()) { Mu("No workouts in this period.", Modifier.padding(top = 12.dp)); return }
        val shades = volumeShades()
        BodyMap(cnt.mapValues { weeklyLevel(it.value / maxOf(1.0, wk)) }, shades, sel = sel) { sel = it }
        MapLegend(shades, listOf("None", "1–3 / wk", "4–9", "10–20", "20+"))
        sel?.let { r -> MuscleDetail(st, r, cnt[r] ?: 0.0, pc[r] ?: 0.0, wk, last[r], regionExercises(cur, exOf, r)) { sel = null } }
        val low = MAJOR.filter { (cnt[it] ?: 0.0) / maxOf(1.0, wk) < 10 }
        if (days >= 7 && low.isNotEmpty()) Panel(Modifier.padding(top = 12.dp)) {
            Txt("Under 10 sets a week", size = 15.sp, weight = FontWeight.SemiBold)
            Mu("${low.joinToString(", ")}. Around 10–20 hard sets a week is the usual range for growth.", Modifier.padding(top = 4.dp), size = 14.sp)
        }
        Panel(Modifier.padding(top = 12.dp)) {
            Txt("Balance", size = 16.sp, weight = FontWeight.SemiBold)
            Mu("Sets per muscle group. Dashed: the previous ${if (days == 365) "year" else "$days days"}.", size = 13.sp)
            BalanceRadar(groupSets(cnt).mapValues { it.value.roundToInt() }, groupSets(pc).mapValues { it.value.roundToInt() }, Modifier.padding(top = 4.dp))
        }
        Label("By muscle")
        val rows = cnt.entries.sortedByDescending { it.value }; val mx = maxOf(1.0, rows.maxOfOrNull { it.value } ?: 0.0)
        rows.forEach { (m, v) ->
            val dv = v - (pc[m] ?: 0.0); val c = shades[weeklyLevel(v / maxOf(1.0, wk))]
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel == m) p.s1 else Color.Transparent)
                .clickable { sel = if (sel == m) null else m }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(8.dp))
                Txt(m, Modifier.width(104.dp), size = 15.sp)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(p.s2)) {
                    Box(Modifier.fillMaxWidth((v / mx).toFloat()).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(c))
                }
                Column(Modifier.width(80.dp).padding(start = 10.dp), horizontalAlignment = Alignment.End) {
                    Txt(setsLabel(v), size = 17.sp, weight = FontWeight.SemiBold)
                    Txt(if (days > 7) "${setsLabel(v / wk)}/wk" else if (dv == 0.0) "same" else "${if (dv > 0) "+" else "−"}${setsLabel(kotlin.math.abs(dv))}", size = 12.sp,
                        color = if (days > 7) p.mu else if (dv > 0) p.ok else if (dv < 0) p.fa else p.mu)
                }
            }
        }
        Mu("Sets are credited to every muscle an exercise works: 1 for the target, ½ for helpers (bench press: chest 1, triceps and front delts ½). Warm-ups are left out. Tap a muscle on the map or in the list.", Modifier.padding(top = 8.dp), size = 13.sp)
    } else {
        val shades = recoveryShades()
        val lv = REGIONS.associateWith { r -> recoveryLevel(last[r]?.let { (now - it) / 3_600_000.0 }) }
        Mu("Muscles that got 2+ sets recently. Most recover in 48–72 hours.", Modifier.padding(top = 10.dp), size = 14.sp)
        BodyMap(lv, shades, sel = sel) { sel = it }
        MapLegend(shades, listOf("Fresh", "48–72 h", "24–48 h", "Under 24 h"))
        val ready = MAJOR.filter { (lv[it] ?: 0) == 0 }; val sore = REGIONS.filter { (lv[it] ?: 0) >= 1 }
        Panel(Modifier.padding(top = 12.dp)) {
            Txt("Ready to train", size = 15.sp, weight = FontWeight.SemiBold)
            Mu(if (ready.isEmpty()) "Everything was trained in the last 3 days." else ready.joinToString(", "), Modifier.padding(top = 4.dp), size = 14.sp)
            if (sore.isNotEmpty()) { Txt("Still recovering", Modifier.padding(top = 10.dp), size = 15.sp, weight = FontWeight.SemiBold); Mu(sore.joinToString(", "), Modifier.padding(top = 4.dp), size = 14.sp) }
        }
        sel?.let { r -> Panel(Modifier.padding(top = 12.dp)) {
            Txt(r, size = 16.sp, weight = FontWeight.SemiBold)
            Mu(last[r]?.let { "Last trained ${ago(now - it)}" } ?: "Not trained recently", Modifier.padding(top = 4.dp), size = 14.sp)
        } }
    }
}

/** What one muscle got in the period, from a tap on the map or the list. */
@Composable
private fun MuscleDetail(st: AppState, r: String, v: Double, pv: Double, wk: Double, last: Long?, exs: List<Pair<String, Double>>, close: () -> Unit) {
    val p = LocalPal.current
    Panel(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(r, Modifier.weight(1f), size = 18.sp, weight = FontWeight.SemiBold)
            IconBtn(Ic.x, "Close", close, size = 32.dp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Stat("Sets", setsLabel(v), Modifier.weight(1f)); Stat("Per week", setsLabel(v / maxOf(1.0, wk)), Modifier.weight(1f))
            Stat("Change", (if (v >= pv) "+" else "−") + setsLabel(kotlin.math.abs(v - pv)), Modifier.weight(1f))
        }
        Mu(last?.let { "Last trained ${ago(st.now - it)}" } ?: "Not trained recently", Modifier.padding(top = 8.dp), size = 14.sp)
        if (exs.isNotEmpty()) {
            Txt("From", Modifier.padding(top = 10.dp), size = 14.sp, weight = FontWeight.SemiBold)
            exs.take(5).forEach { (e, n) ->
                Row(Modifier.fillMaxWidth().clickable { st.push(Screen.ExerciseDetail(e)) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(st.idx.exOf(e).name, Modifier.weight(1f), size = 15.sp, maxLines = 1); Txt(setsLabel(n), size = 15.sp, color = p.mu)
                }
            }
        }
    }
}

private fun ago(ms: Long): String { val h = ms / 3_600_000; return when { h < 1 -> "just now"; h < 24 -> "${h} h ago"; h < 48 -> "yesterday"; else -> "${h / 24} days ago" } }

/** A rounded panel for a section of the Muscles screen. */
@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().card(16.dp), content = content)

private data class Ag(val n: Int, val t: Long, val v: Double, val s: Int, val p: Int, val d: Int)

@Composable
internal fun ReportView(st: AppState) {
    val idx = st.idx; val lb = LocalLb.current
    val today = ldt(st.now).date
    val a: Long; val b: Long; val pa: Long; val label: String
    if (st.rp == "year") {
        val y = today.year + st.ro; a = monthStart(y, 1); b = monthStart(y + 1, 1); pa = monthStart(y - 1, 1); label = y.toString()
    } else {
        val (y, m) = addMonths(today.year, today.monthNumber, st.ro); a = monthStart(y, m)
        b = addMonths(y, m, 1).let { monthStart(it.first, it.second) }; pa = addMonths(y, m, -1).let { monthStart(it.first, it.second) }; label = monthLabel(y, m)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ic.left, "Previous", { st.ro -= 1 })
        Txt(label, Modifier.weight(1f), size = 18.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
        IconBtn(Ic.right, "Next", { if (st.ro < 0) st.ro += 1 })
    }
    ChipRow { Chip("Month", st.rp == "month") { st.rp = "month"; st.ro = 0 }; Chip("Year", st.rp == "year") { st.rp = "year"; st.ro = 0 } }
    val cw = idx.ws.filter { it.start in a until b }; val pw = idx.ws.filter { it.start in pa until a }
    if (cw.isEmpty()) { Mu("No workouts in this period.", Modifier.padding(top = 16.dp)); return }
    fun ag(ws: List<Workout>) = Ag(ws.size, ws.sumOf { it.end - it.start }, ws.sumOf { idx.wVol(it) }, ws.sumOf { idx.wSets(it) }, ws.sumOf { idx.prm[it.id]?.n ?: 0 }, ws.map { ldt(it.start).date }.distinct().size)
    val c = ag(cw); val o = ag(pw)
    fun dl(x: Double, y: Double, f: (Double) -> String) = if (pw.isEmpty()) "" else "${if (x >= y) "+" else "−"}${f(kotlin.math.abs(x - y))} vs previous"
    val tiles = listOf(
        Triple("Workouts", c.n.toString(), dl(c.n.toDouble(), o.n.toDouble()) { it.roundToInt().toString() }),
        Triple("Time", dur(c.t), dl(c.t.toDouble(), o.t.toDouble()) { dur(it.toLong()) }),
        Triple("Volume", wv(c.v, lb), dl(c.v, o.v) { wv(it, lb) }),
        Triple("Sets", c.s.toString(), dl(c.s.toDouble(), o.s.toDouble()) { it.roundToInt().toString() }),
        Triple("Records", c.p.toString(), dl(c.p.toDouble(), o.p.toDouble()) { it.roundToInt().toString() }),
        Triple("Days trained", c.d.toString(), "avg ${dur(c.t / c.n)} a session"),
    )
    tiles.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (l, v, s) -> Column(Modifier.weight(1f).card(12.dp)) { Mu(l, size = 13.sp); Txt(v, size = 22.sp, weight = FontWeight.Bold, maxLines = 1); if (s.isNotEmpty()) Mu(s, size = 12.sp) } }
        }
    }
    compareWeight(c.v).takeIf { it.isNotEmpty() }?.let { Mu("$it lifted in total", Modifier.padding(top = 12.dp), size = 15.sp) }
    val mus = HashMap<String, Int>(); val exs = HashMap<String, Int>()
    cw.forEach { w -> w.exercises.forEach { it2 -> val x = idx.exOf(it2.eid); mus[x.muscle] = (mus[x.muscle] ?: 0) + working(it2.sets).size; exs[it2.eid] = (exs[it2.eid] ?: 0) + 1 } }
    fun bo(l: List<HEntry>) = l.maxOfOrNull { e -> working(e.sets).maxOfOrNull { setM(it).orm } ?: 0.0 } ?: 0.0
    val gains = exs.keys.filter { isVol(idx.exOf(it).type) }.mapNotNull { e ->
        val h = idx.h[e] ?: return@mapNotNull null; val now1 = bo(h.filter { it.start in a until b }); val bef = bo(h.filter { it.start < a })
        if (bef > 0 && (now1 - bef) / bef > 0.001) Triple(e, bef, now1) else null
    }.sortedByDescending { (it.third - it.second) / it.second }.take(5)
    if (gains.isNotEmpty()) {
        H3("Biggest strength gains")
        gains.forEach { (e, bef, now1) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Txt(idx.exOf(e).name, size = 15.sp); Mu("Est. 1RM ${wt(r1(bef), lb)} → ${wt(r1(now1), lb)}", size = 13.sp) }
                Txt("+${fmtN(r1((now1 - bef) / bef * 100))}%", size = 18.sp, weight = FontWeight.SemiBold)
            }
            Divider()
        }
    }
    H3("Most-trained muscles")
    mus.entries.sortedByDescending { it.value }.take(6).forEach { (m, v) -> Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) { Txt(m, Modifier.weight(1f), size = 15.sp); Txt("$v sets", weight = FontWeight.SemiBold) }; Divider() }
    H3("Most-done exercises")
    exs.entries.sortedByDescending { it.value }.take(5).forEach { (e, v) -> Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) { Txt(idx.exOf(e).name, Modifier.weight(1f), size = 15.sp); Txt("$v×", weight = FontWeight.SemiBold) }; Divider() }
}
