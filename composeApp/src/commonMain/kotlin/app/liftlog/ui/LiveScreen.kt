package app.liftlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.*
import app.liftlog.model.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** What an exercise card edits: the live workout or a routine draft. */
class CardCtx(val live: Boolean, val start: Long, val editId: String?, val active: Active?, val items: List<ExItem>, val set: (List<ExItem>) -> Unit)

/** Hint shown in an empty field: suggestion, then routine target, then last session, then the set above. */
fun phv(item: ExItem, si: Int, f: String, pv: WSet?, sg: Sug?, lb: Boolean): String {
    sg?.let { fieldVal(it.asSet(), f, lb)?.let { v -> return plainN(v) } }
    val s = item.sets[si]
    fieldVal(valsAsSet(s.p), f, lb)?.let { return plainN(it) }
    fieldVal(pv, f, lb)?.let { return plainN(it) }
    // Otherwise the nearest set above of the same kind (a warm-up never hints a working set).
    val j = (si - 1 downTo 0).firstOrNull { (item.sets[it].t == "w") == (s.t == "w") } ?: return ""
    fieldVal(item.sets[j], f, lb)?.let { return plainN(it) }
    return phv(item, j, f, null, null, lb)
}

@Composable
fun LiveScreen(st: AppState) {
    val a = st.data.active
    if (a == null) { LaunchedEffect(Unit) { st.stack.removeAll { it is Screen.Live } }; return }
    val idx = st.idx; val lb = LocalLb.current; val p = LocalPal.current; val ed = a.editId != null
    val (vol, sets) = idx.liveStats(a)
    Column(Modifier.fillMaxSize()) {
        TopBar(if (ed) "Edit workout" else "Log workout",
            left = { IconBtn(Ic.down, "Minimise", { st.minimize() }) },
            right = { Btn(if (ed) "Save" else "Finish", { openFinish(st) }, kind = BtnKind.Primary, small = true) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Stat("Duration", if (ed) dur((a.end ?: a.start) - a.start) else clock(st.now - a.start), Modifier.weight(1f), accent = true)
            Stat("Volume", wv(vol, lb), Modifier.weight(1f))
            Stat("Sets", sets.toString(), Modifier.weight(1f))
        }
        Divider()
        val ctx = CardCtx(true, a.start, a.editId, a, a.exercises) { st.setItems(it) }
        val ss = ssInfo(a.exercises)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            if (a.exercises.isEmpty()) item { EmptyState(Ic.dumb, "Get started", "Add an exercise to start your workout.") }
            items(a.exercises.size) { ei -> ExerciseCard(st, ctx, ei, ss(a.exercises[ei].ss)) }
            item {
                Btn("Add exercise", { pickToAdd(st, ctx) }, Modifier.fillMaxWidth().padding(top = 16.dp), kind = BtnKind.Primary, icon = Ic.plus)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Minimise", { st.minimize() }, Modifier.weight(1f))
                    Btn(if (ed) "Cancel edit" else "Discard workout", {
                        st.confirm(if (ed) "Cancel editing?" else "Discard this workout?", if (ed) "Your changes are dropped; the saved workout stays as it was." else "Everything logged in this session is lost.", if (ed) "Drop changes" else "Discard workout") { st.discardActive() }
                    }, Modifier.weight(1f), kind = BtnKind.Danger)
                }
            }
        }
        if (st.restEnd > 0) RestBar(st)
    }
}

@Composable
private fun RestBar(st: AppState) {
    val p = LocalPal.current
    val rem = st.restEnd - st.now
    Column(Modifier.fillMaxWidth().background(p.s1)) {
        Box(Modifier.fillMaxWidth((rem.toFloat() / st.restTotal).coerceIn(0f, 1f)).height(3.dp).background(p.ac))
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("−15", { st.shiftRest(-15000) }, small = true)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Big(clock(rem + 999), size = 32.sp); Mu("Rest", size = 13.sp)
            }
            Btn("+15", { st.shiftRest(15000) }, small = true)
            Btn("Skip", { st.stopRest() }, small = true, kind = BtnKind.Primary)
        }
    }
}

fun pickToAdd(st: AppState, ctx: CardCtx) {
    st.push(Screen.Picker("add") { ids -> ctx.set(ctx.items + ids.map { st.blankItem(it) }) })
}

@Composable
fun ExerciseCard(st: AppState, ctx: CardCtx, ei: Int, ssi: Pair<Char, Long>?) {
    val idx = st.idx; val lb = LocalLb.current; val p = LocalPal.current
    val it = ctx.items.getOrNull(ei) ?: return
    val x = idx.exOf(it.eid); val T = typeOf(x.type); val live = ctx.live; val tm = live && x.type == "dur"
    val prev = idx.prevSets(it.eid, ctx.start, ctx.editId)
    val prs = if (live && ctx.active != null) idx.livePrs(ctx.active, ei) else emptyMap()
    val pin = idx.pin(it.eid)
    fun upd(f: (ExItem) -> ExItem) { ctx.set(ctx.items.toMutableList().also { l -> l[ei] = f(l[ei]) }) }
    val sgs = it.sets.indices.map { si -> if (live && ctx.active != null && it.sets[si].done != true) idx.sugFor(ctx.active, ei, si) else null }
    val barColor = ssi?.let { Color(it.second) }
    Column(
        Modifier.fillMaxWidth().then(if (barColor != null) Modifier.drawBehind { drawRect(barColor, Offset.Zero, androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }.padding(start = 10.dp) else Modifier)
            .padding(vertical = 12.dp),
    ) {
        if (ssi != null) Txt("Superset ${ssi.first}", size = 13.sp, weight = FontWeight.SemiBold, color = barColor!!)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(x.name, Modifier.weight(1f).clickable { st.push(Screen.ExerciseDetail(x.id)) }.padding(vertical = 8.dp), size = 17.sp, weight = FontWeight.SemiBold, color = p.ac)
            IconBtn(Ic.dots, "Exercise options", { exerciseMenu(st, ctx, ei) })
        }
        if (pin.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(p.acs).padding(horizontal = 10.dp, vertical = 7.dp)) {
            Icon(Ic.pin, null, Modifier.size(16.dp), tint = p.ac); Spacer(Modifier.width(8.dp)); Txt(pin, size = 14.sp)
        }
        if (live && idx.stalled(it.eid)) Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.warn, null, Modifier.size(16.dp), tint = p.wu); Spacer(Modifier.width(6.dp)); Txt("No progress in 4 weeks. Try a lighter deload week or a variation.", size = 14.sp, color = p.wu)
        }
        BasicTextField(it.note ?: "", { v -> upd { e -> e.copy(note = v) } }, Modifier.fillMaxWidth().padding(vertical = 2.dp),
            textStyle = TextStyle(color = p.ink, fontSize = 15.sp), cursorBrush = SolidColor(p.ac),
            decorationBox = { inner -> Box { if ((it.note ?: "").isEmpty()) Mu("Add notes here…", size = 15.sp); inner() } })
        Row(Modifier.clickable {
            st.showMenu("Rest timer", RESTS.map { s -> MenuItem(fmtRest(s), on = s == (it.rest ?: 0)) { upd { e -> e.copy(rest = s) } } })
        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.clock, null, Modifier.size(16.dp), tint = p.ac); Spacer(Modifier.width(6.dp)); Txt("Rest timer: ${fmtRest(it.rest)}", size = 14.sp, weight = FontWeight.Medium, color = p.ac)
        }
        if (sgs.any { s -> s?.up == true }) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.up, null, Modifier.size(16.dp), tint = p.ac); Spacer(Modifier.width(6.dp)); Txt("Heavier weight suggested: you hit your reps last time", size = 14.sp, color = p.ac)
        }
        // header
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Mu("Set", Modifier.width(36.dp), size = 13.sp); Mu("Previous", Modifier.weight(1f), size = 13.sp)
            T.f.forEachIndexed { k, f -> Txt(hdr(x.type, f, T.h[k], lb), Modifier.width(if (T.f.size == 1) 84.dp else if (k == 0) 70.dp else 58.dp), size = 13.sp, weight = FontWeight.SemiBold, color = p.mu, align = TextAlign.Center) }
            if (tm) Mu("Timer", Modifier.width(40.dp), size = 13.sp)
            if (live) Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { Icon(Ic.check, null, Modifier.size(16.dp), tint = p.mu) }
        }
        it.sets.forEachIndexed { si, s ->
            val done = live && s.done == true
            val pv = prevFor(prev, it.sets, si)
            val run = st.timer?.let { t -> live && t.ei == ei && t.si == si } == true
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(10.dp)).background(if (done) p.oks else Color.Transparent).padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(36.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(if (done) Color.Transparent else p.s1).clickable { setMenu(st, ctx, ei, si) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val lab = setLabel(it.sets, si)
                    Txt(lab, size = 17.sp, weight = FontWeight.Bold, color = when (s.t) { "w" -> p.wu; "d" -> p.dr; "f" -> p.fa; else -> p.ink })
                    s.rpe?.let { r -> Mu("@${fmtN(r)}", size = 11.sp) }
                }
                Row(Modifier.weight(1f).height(38.dp).clickable {
                    if (pv != null && s.done != true) upd { e -> e.copy(sets = e.sets.toMutableList().also { l -> l[si] = l[si].copy(kg = pv.kg, reps = pv.reps, km = pv.km, sec = pv.sec) }) }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Txt(if (pv != null) fmtSet(x.type, pv, lb) else "—", Modifier.weight(1f, fill = false), size = 14.sp, color = p.mu, maxLines = 1)
                    if (prs[si] != null) { Spacer(Modifier.width(4.dp)); Icon(Ic.trophy, "Personal record", Modifier.size(16.dp), tint = p.pr) }
                }
                T.f.forEachIndexed { k, f ->
                    val ph = phv(it, si, f, pv, sgs[si], lb)
                    val sgHas = sgs[si]?.let { g -> fieldVal(g.asSet(), f, lb) != null } == true
                    val override = if (run && f == "sec") st.timer!!.let { t -> val el = (st.now - t.start) / 1000.0; if (t.tgt > 0) ceil(t.tgt - el).coerceAtLeast(0.0).roundToInt().toString() else floor(el).roundToInt().toString() } else null
                    NumCell(fieldVal(s, f, lb), ph, { v -> upd { e -> e.copy(sets = e.sets.toMutableList().also { l -> l[si] = withField(l[si], f, v, lb) }) } },
                        Modifier.width(if (T.f.size == 1) 84.dp else if (k == 0) 70.dp else 58.dp), integer = f == "reps" || f == "sec", accentPh = sgHas, filled = !done, override = override)
                }
                if (tm) Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(if (run) p.ac else p.s2).clickable { toggleTimer(st, ei, si) }, contentAlignment = Alignment.Center) {
                    Icon(if (run) Ic.stop else Ic.play, if (run) "Stop timer" else "Start timer", Modifier.size(18.dp), tint = if (run) Color.White else p.ac)
                }
                val focus = LocalFocusManager.current
                if (live) Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(if (done) p.ok else p.s2).clickable { focus.clearFocus(); checkSet(st, ei, si) }, contentAlignment = Alignment.Center) {
                    Icon(Ic.check, "Complete set", Modifier.size(20.dp), tint = if (done) Color.White else p.mu)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(p.s1).clickable {
                upd { e -> val last = e.sets.lastOrNull(); val pp = last?.let { l -> if (l.kg != null || l.reps != null || l.km != null || l.sec != null) SetVals(l.kg, l.reps, l.km, l.sec) else l.p }; e.copy(sets = e.sets + WSet(p = pp)) }
            }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.plus, null, Modifier.size(16.dp), tint = p.ink); Spacer(Modifier.width(6.dp)); Txt("Add set", size = 15.sp, weight = FontWeight.SemiBold)
            }
            // Warm-ups are one tap away until the exercise has some.
            if (isVol(x.type) && it.sets.none { s -> s.t == "w" } && it.sets.none { s -> s.done == true }) Row(Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(p.s1).clickable { addWarmups(st, ctx, ei) }.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.up, null, Modifier.size(16.dp), tint = p.wu); Spacer(Modifier.width(6.dp)); Txt("Warm-up", size = 15.sp, weight = FontWeight.SemiBold)
            }
        }
    }
    Divider()
}

/** Ticks a set (filling empty fields from the hints) or unticks it. Also used by the notification's "Complete set". */
fun checkSet(st: AppState, ei: Int, si: Int) {
    val a = st.data.active ?: return; val idx = st.idx; val lb = st.lb
    val it = a.exercises.getOrNull(ei) ?: return; val s = it.sets.getOrNull(si) ?: return
    val T = typeOf(idx.exOf(it.eid).type)
    val ns: WSet
    if (s.done != true) {
        val pv = prevFor(idx.prevSets(it.eid, a.start, a.editId), it.sets, si); val sg = idx.sugFor(a, ei, si)
        var q = s
        T.f.forEach { f -> if (fieldVal(q, f, lb) == null) parseNum(phv(it, si, f, pv, sg, lb))?.let { v -> q = withField(q, f, v, lb) } }
        if (T.f.all { f -> fieldVal(q, f, lb) == null }) { st.say("Enter a value first"); return }
        setProblem(idx.exOf(it.eid).type, q)?.let { st.say(it); return }
        ns = q.copy(done = true)
        if (a.editId == null) {
            val nx = a.exercises.getOrNull(ei + 1)
            if (it.ss != null && nx?.ss == it.ss) st.say("Superset: next is ${idx.exOf(nx!!.eid).name}") else st.startRest(it.rest)
        }
    } else ns = s.copy(done = false)
    val before = idx.livePrs(a, ei)[si]
    st.setItems(a.exercises.toMutableList().also { l -> l[ei] = it.copy(sets = it.sets.toMutableList().also { ss -> ss[si] = ns }) })
    // A record on the set just ticked gets its own toast (it wins over the superset hint).
    val na = st.data.active ?: return
    val now = idx.livePrs(na, ei)[si]
    if (ns.done == true && now != null && now != before) {
        val x = idx.exOf(it.eid)
        if (st.celebrations) {
            val k = now.first(); val m = Mascots.forRecord(k)
            val more = if (now.size > 1) " (+${now.size - 1} more)" else ""
            st.celebration = Celebration(m, "New record!", "${x.name}\n${PRL[k] ?: k}: ${prValue(k, ns, st.lb)}$more", Mascots.quip(m))
        } else st.say("🏆 New record: ${x.name}, " + now.joinToString(", ") { k -> (PRL[k] ?: k).lowercase() })
        st.platform.celebrate()
    }
}

private fun toggleTimer(st: AppState, ei: Int, si: Int) {
    val t = st.timer
    if (t != null && t.ei == ei && t.si == si) { st.finishTimer(false); return }
    if (t != null) st.finishTimer(false)
    val a = st.data.active ?: return; val it = a.exercises[ei]; val s = it.sets[si]
    if (s.done == true) { st.say("Untick the set to time it again"); return }
    val pv = prevFor(st.idx.prevSets(it.eid, a.start, a.editId), it.sets, si)
    val tgt = s.sec ?: parseNum(phv(it, si, "sec", pv, null, st.lb)) ?: 0.0
    st.timer = SetTimer(ei, si, nowMs(), tgt)
}

private fun setMenu(st: AppState, ctx: CardCtx, ei: Int, si: Int) {
    val it = ctx.items[ei]; val s = it.sets[si]
    fun upd(ns: List<WSet>) = ctx.set(ctx.items.toMutableList().also { l -> l[ei] = it.copy(sets = ns) })
    val items = SET_TYPES.map { (k, l) -> MenuItem(l, on = s.t == k) { upd(it.sets.toMutableList().also { q -> q[si] = s.copy(t = k) }) } }.toMutableList()
    if (ctx.live) items += MenuItem(if (s.rpe != null) "RPE ${fmtN(s.rpe)} (change)" else "Log RPE") {
        val opts = listOf(10.0 to "10 · max effort", 9.5 to "9.5", 9.0 to "9 · one rep left", 8.5 to "8.5", 8.0 to "8 · two reps left", 7.5 to "7.5", 7.0 to "7 · three reps left", 6.5 to "6.5", 6.0 to "6 · four+ left")
        st.showMenu("RPE: how hard was it?", opts.map { (v, l) -> MenuItem(l, on = s.rpe == v) { upd(it.sets.toMutableList().also { q -> q[si] = s.copy(rpe = v) }) } } +
            (if (s.rpe != null) listOf(MenuItem("Clear RPE", danger = true) { upd(it.sets.toMutableList().also { q -> q[si] = s.copy(rpe = null) }) }) else emptyList()))
    }
    items += MenuItem("Remove set", danger = true) {
        val snap = ctx.items
        upd(it.sets.toMutableList().also { q -> q.removeAt(si) })
        st.say("Set removed") { ctx.set(snap) }
    }
    st.showMenu("Set", items)
}

private fun exerciseMenu(st: AppState, ctx: CardCtx, ei: Int) {
    val idx = st.idx; val lb = st.lb; val items = ctx.items; val n = items.size; val it = items[ei]; val x = idx.exOf(it.eid); val nx = items.getOrNull(ei + 1)
    val wt = isVol(x.type)
    fun setAll(l: List<ExItem>) = ctx.set(normSS(l))
    val m = mutableListOf<MenuItem>()
    if (ei < n - 1 && !(it.ss != null && nx?.ss == it.ss)) m += MenuItem("Superset with next exercise") {
        val g = it.ss ?: nx!!.ss ?: ((items.mapNotNull { e -> e.ss }.maxOrNull() ?: 0) + 1)
        val old = nx!!.ss
        setAll(items.mapIndexed { i, e -> when { i == ei || i == ei + 1 -> e.copy(ss = g); old != null && e.ss == old -> e.copy(ss = g); else -> e } })
    }
    if (it.ss != null) m += MenuItem("Remove from superset") { setAll(items.toMutableList().also { l -> l[ei] = it.copy(ss = null) }) }
    if (wt) m += MenuItem("Add warm-up sets") { addWarmups(st, ctx, ei) }
    if (wt && x.equip == "Barbell") m += MenuItem("Plate calculator") { st.push(Screen.Plate(workWeight(st, ctx, ei)?.let { w -> plainN(w) } ?: "")) }
    m += MenuItem(if (idx.pin(it.eid).isNotEmpty()) "Edit pinned note" else "Pin a note") { st.push(Screen.PinEditor(it.eid, idx.pin(it.eid))) }
    if (ei > 0) m += MenuItem("Move up") { setAll(items.toMutableList().also { l -> l[ei] = items[ei - 1]; l[ei - 1] = it }) }
    if (ei < n - 1) m += MenuItem("Move down") { setAll(items.toMutableList().also { l -> l[ei] = items[ei + 1]; l[ei + 1] = it }) }
    m += MenuItem("Replace exercise") {
        st.push(Screen.Picker("replace") { ids -> val cur = ctx.items; ctx.set(cur.toMutableList().also { l -> if (ei < l.size) l[ei] = l[ei].copy(eid = ids[0], rest = idx.lastRest(ids[0]), sets = l[ei].sets.map { s -> WSet(t = s.t) }) }) })
    }
    m += MenuItem("Remove exercise", danger = true) {
        val snap = items
        setAll(items.toMutableList().also { l -> l.removeAt(ei) })
        st.say("${x.name} removed") { ctx.set(snap) }
    }
    st.showMenu(x.name, m)
}

/** Working weight of an exercise: the first working set's value, or its hint. */
private fun workWeight(st: AppState, ctx: CardCtx, ei: Int): Double? {
    val it = ctx.items.getOrNull(ei) ?: return null; val lb = st.lb
    val si0 = it.sets.indexOfFirst { s -> s.t != "w" }.coerceAtLeast(0); val s = it.sets.getOrNull(si0) ?: return null
    val pv = prevFor(st.idx.prevSets(it.eid, ctx.start, ctx.editId), it.sets, si0)
    val sg = if (ctx.live && ctx.active != null) st.idx.sugFor(ctx.active, ei, si0) else null
    return fieldVal(s, "kg", lb) ?: parseNum(phv(it, si0, "kg", pv, sg, lb))
}

/** Replaces an exercise's warm-up sets with a ramp to its working weight. */
fun addWarmups(st: AppState, ctx: CardCtx, ei: Int) {
    val it = ctx.items.getOrNull(ei) ?: return; val x = st.idx.exOf(it.eid); val lb = st.lb
    val w = workWeight(st, ctx, ei)
    if (w == null || w <= 0) { st.say("Enter your working weight first"); return }
    val bar = if (x.equip == "Barbell") BARS.getValue(unitOf(lb))[0] else 0.0
    val list = warmupPlan(w, bar, lb)
    if (list.isEmpty()) { st.say("Working weight is too light for warm-ups"); return }
    ctx.set(normSS(ctx.items.toMutableList().also { l -> l[ei] = it.copy(sets = list.map { (q, r) -> WSet(t = "w", p = SetVals(kg = fromU(q, lb), reps = r)) } + it.sets.filter { s -> s.t != "w" }) }))
    st.say(count(list.size, "warm-up set") + " added: " + list.joinToString(", ") { (q, r) -> "${fmtN(q)}×${fmtN(r)}" })
}

/* ---------- finish ---------- */
fun openFinish(st: AppState) {
    val a = st.data.active ?: return
    if (a.exercises.none { e -> e.sets.any { it.done == true } }) { st.say("Complete at least one set first"); return }
    val min = if (a.editId != null) (((a.end ?: a.start) - a.start) / 60000).toInt() else maxOf(1, ((nowMs() - a.start) / 60000).toInt())
    st.push(Screen.Finish(a.title?.ifBlank { null } ?: defTitle(), a.start, min, a.notes ?: ""))
}

@Composable
fun FinishScreen(st: AppState, f: Screen.Finish) {
    val a = st.data.active ?: return
    val idx = st.idx; val lb = LocalLb.current; val p = LocalPal.current
    val (vol, sets) = idx.liveStats(a)
    val prs = a.exercises.indices.sumOf { ei -> idx.livePrs(a, ei).values.sumOf { it.size } }
    val inc = a.exercises.sumOf { e -> e.sets.count { it.done != true } }
    val r = a.routineId?.let { st.data.routines[it] }
    var whenText by remember { mutableStateOf(ldt(f.whenMs).let { d -> "${d.date} ${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}" }) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Save workout", left = { IconBtn(Ic.left, "Back", { st.close(f) }) }, right = { Btn("Save", { st.saveWorkout(f) }, kind = BtnKind.Primary, small = true) })
        Body {
            BasicTextField(f.title, { f.title = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true,
                textStyle = TextStyle(color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(p.ac))
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Stat("Duration", dur((parseNum(f.min) ?: 0.0).toLong() * 60000), Modifier.weight(1f)); Stat("Volume", wv(vol, lb), Modifier.weight(1f))
                Stat("Sets", sets.toString(), Modifier.weight(1f)); Stat("Records", prs.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Started (YYYY-MM-DD HH:MM)", whenText, { t -> whenText = t; parseDate(t.replace(' ', 'T'))?.let { f.whenMs = it } }, Modifier.weight(1.4f))
                Field("Duration (min)", f.min, { f.min = it }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
            }
            Field("Notes", f.notes, { f.notes = it }, placeholder = "How did it go?", singleLine = false, minLines = 3)
            if (r != null) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Update “${r.name}” routine with these sets", Modifier.weight(1f), size = 15.sp); Toggle(f.upd) { f.upd = !f.upd }
            }
            val recs = liveRecords(idx, a, lb)
            if (recs.isNotEmpty()) RecordList(recs)
            MusclesWorked(muscleSets(idx, a.exercises, doneOnly = true))
            if (inc > 0) Txt("$inc incomplete set${if (inc > 1) "s" else ""} will be left out.", size = 14.sp, color = p.wu)
            Btn("Save workout", { st.saveWorkout(f) }, Modifier.fillMaxWidth().padding(top = 16.dp), kind = BtnKind.Primary)
        }
    }
}

/** "New records" box with a trophy per line. */
@Composable
fun RecordList(recs: List<RecordLine>) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(p.pr.copy(alpha = 0.12f)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.trophy, null, Modifier.size(18.dp), tint = p.pr); Spacer(Modifier.width(8.dp))
            Txt(if (recs.size == 1) "New record" else "${recs.size} new records", size = 16.sp, weight = FontWeight.SemiBold, color = p.pr)
        }
        recs.forEach { r ->
            Row(Modifier.padding(top = 6.dp)) {
                Txt(r.exercise, Modifier.weight(1f), size = 14.sp, maxLines = 1)
                Txt(r.what, Modifier.padding(start = 8.dp), size = 14.sp, weight = FontWeight.SemiBold)
            }
        }
    }
}
