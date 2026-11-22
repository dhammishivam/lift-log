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
            Column(Modifier.fillMaxWidth().card(20.dp)) {
                Txt("No workouts yet", weight = FontWeight.SemiBold)
                Mu("Finished workouts land here with their time, volume and records. Coming from Hevy? Bring your whole history across in Settings.", Modifier.padding(top = 6.dp, bottom = 14.dp), size = 15.sp)
                FlowRowX { Btn("Import from Hevy", { st.push(Screen.SettingsScreen) }, kind = BtnKind.Primary); Btn("Start a workout", { st.tab = Tab.Workout }) }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Seg(listOf("list" to "Workouts", "cal" to "Calendar", "mus" to "Muscles", "rep" to "Report"), st.hv, { st.hv = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), fill = true)
        when (st.hv) {
            "cal" -> Body { CalendarView(st) }
            "mus" -> Body { MusclesView(st) }
            "rep" -> Body { ReportView(st) }
            else -> HistoryList(st)
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
private fun CalendarView(st: AppState) {
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
private fun MusclesView(st: AppState) {
    val idx = st.idx; val p = LocalPal.current
    val days = st.mu; val now = st.now; val from = now - days * 86_400_000L; val pf = from - days * 86_400_000L
    val cnt = HashMap<String, Int>(); val pc = HashMap<String, Int>()
    idx.ws.forEach { w ->
        val tg = if (w.start >= from) cnt else if (w.start >= pf) pc else null
        tg?.let { m -> w.exercises.forEach { it2 -> val mu = idx.exOf(it2.eid).muscle; m[mu] = (m[mu] ?: 0) + working(it2.sets).size } }
    }
    val rows = cnt.entries.sortedByDescending { it.value }; val mx = maxOf(1, rows.maxOfOrNull { it.value } ?: 0); val tot = rows.sumOf { it.value }; val wk = days / 7.0
    ChipRow { listOf(7 to "7 days", 30 to "30 days", 90 to "90 days").forEach { (k, l) -> Chip(l, st.mu == k) { st.mu = k } } }
    Big("$tot sets", Modifier.padding(top = 12.dp))
    Mu("Working sets in the last $days days" + if (days > 7) " · about ${(tot / wk).let { r -> fmtN(if (r < 10) (r * 10).roundToInt() / 10.0 else r.roundToInt().toDouble()) }} a week" else "")
    if (rows.isNotEmpty()) BodyMap(cnt)
    rows.forEach { (m, v) ->
        val pv = pc[m] ?: 0; val dv = v - pv
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(m, Modifier.width(110.dp), size = 15.sp)
            Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(p.s2)) {
                Box(Modifier.fillMaxWidth(v.toFloat() / mx).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(p.ac))
            }
            Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
                Txt(v.toString(), size = 18.sp, weight = FontWeight.SemiBold)
                Mu(if (days > 7) "${fmtN(r1(v / wk))}/wk" else if (dv != 0) "${if (dv > 0) "+" else "−"}${kotlin.math.abs(dv)} vs prev" else "same as prev", size = 12.sp)
            }
        }
        Divider()
    }
    if (rows.isEmpty()) Mu("No workouts in this period.", Modifier.padding(top = 12.dp))
    val miss = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes", "Calves", "Abs").filter { it !in cnt }
    if (miss.isNotEmpty() && rows.isNotEmpty()) Mu("Not trained in this period: ${miss.joinToString(", ")}.", Modifier.padding(top = 12.dp))
    Mu("Each set counts toward the exercise's main muscle. Warm-ups are left out.", Modifier.padding(top = 8.dp))
}

private data class Ag(val n: Int, val t: Long, val v: Double, val s: Int, val p: Int, val d: Int)

@Composable
private fun ReportView(st: AppState) {
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
