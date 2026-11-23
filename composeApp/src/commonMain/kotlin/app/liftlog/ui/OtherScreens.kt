package app.liftlog.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.liftlog.logic.*
import app.liftlog.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

@Composable
fun PhotoThumb(st: AppState, id: String, modifier: Modifier, crop: Boolean = true) {
    val p = LocalPal.current
    val bmp by produceState<ImageBitmap?>(null, id) { value = withContext(Dispatchers.Default) { st.platform.loadPhoto(id) } }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(p.s1), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, "Progress photo", Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
    }
}


/* ---------- body tab ---------- */
@Composable
fun BodyTab(st: AppState) {
    val idx = st.idx; val lb = LocalLb.current; val p = LocalPal.current
    if (idx.ms.isEmpty()) {
        Body {
            Column(Modifier.fillMaxWidth().card(20.dp)) {
                Txt("No measurements yet", weight = FontWeight.SemiBold)
                Mu("Log your weight, body fat and tape measurements to see the trend. Your Hevy measurements export imports here too.", Modifier.padding(top = 6.dp, bottom = 14.dp), size = 15.sp)
                FlowRowX { Btn("Log measurements", { newMeasure(st) }, kind = BtnKind.Primary); Btn("Import from Hevy", { st.push(Screen.SettingsScreen) }) }
            }
        }
        return
    }
    val have = MF.filter { (k) -> idx.ms.any { it.v[k] != null } }
    val f = have.firstOrNull { it.first == st.mk } ?: have.first(); val k = f.first
    val ser = idx.ms.filter { it.v[k] != null }; val last = ser[0]; val prev = ser.getOrNull(1)
    val from = when (st.mr) { "3M" -> st.now - 90L * 86_400_000; "1Y" -> st.now - 365L * 86_400_000; else -> 0L }
    val pts = ser.filter { it.date >= from }.map { it.date to mShow(k, it.v[k]!!, lb) }.reversed()
    val photos = idx.ms.filter { !it.photos.isNullOrEmpty() }
    Body {
        Big(mFmt(k, last.v[k]!!, lb), size = 34.sp)
        Mu("${f.second} · ${dLong(last.date)}", size = 15.sp)
        if (prev != null) {
            val dv = last.v[k]!! - prev.v[k]!!; val ys = ser.map { it.v[k]!! }
            Mu("${if (dv > 0) "+" else if (dv < 0) "−" else "±"}${mFmt(k, kotlin.math.abs(dv), lb)} since ${dShort(prev.date)} · range ${mFmt(k, ys.min(), lb)} to ${mFmt(k, ys.max(), lb)}", size = 15.sp)
        }
        LineChart(listOf(Series(pts, p.ac)), Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp))
        ChipRow { listOf("3M" to "3 months", "1Y" to "1 year", "All" to "All time").forEach { (kk, l) -> Chip(l, st.mr == kk) { st.mr = kk } } }
        if (have.size > 1) ChipRow { have.forEach { (kk, l) -> Chip(l, kk == k) { st.mk = kk } } }
        Btn("Log measurements", { newMeasure(st) }, Modifier.fillMaxWidth().padding(top = 16.dp), kind = BtnKind.Primary, icon = Ic.plus)
        if (photos.isNotEmpty()) {
            Label("Progress photos")
            val flat = photos.flatMap { m -> m.photos!!.map { m to it } }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEach { m ->
                    Column(Modifier.width(96.dp).clickable { st.push(Screen.Photos(flat.indexOfFirst { it.first.id == m.id })) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoThumb(st, m.photos!![0], Modifier.size(96.dp, 128.dp)); Mu(dShort(m.date), size = 13.sp)
                    }
                }
            }
        }
        Label("Entries")
        idx.ms.forEach { m ->
            val ks = MF.filter { (kk) -> m.v[kk] != null }; val main = ks.firstOrNull { it.first == k } ?: ks.firstOrNull()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { st.push(Screen.MeasureEditor(m, false)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(dLong(m.date), size = 15.sp)
                    Mu((if (ks.size > 1) "${ks.size} measurements" else main?.second ?: "") + (m.photos?.takeIf { it.isNotEmpty() }?.let { " · ${it.size} photo${if (it.size > 1) "s" else ""}" } ?: ""), size = 13.sp)
                }
                if (main != null) Txt(mFmt(main.first, m.v[main.first]!!, lb), size = 19.sp, weight = FontWeight.SemiBold)
            }
            Divider()
        }
    }
}

private fun newMeasure(st: AppState) {
    val d = ldt(nowMs()).date
    st.push(Screen.MeasureEditor(Measure(id = "m_" + rid(), date = startOfDay(d) + 12 * 3_600_000L), true))
}

@Composable
fun MeasureEditorScreen(st: AppState, s: Screen.MeasureEditor) {
    val lb = LocalLb.current; val p = LocalPal.current; val e = s.e; val latest = st.idx.ms.firstOrNull()
    var dateText by remember { mutableStateOf(ldt(e.date).date.toString()) }
    fun save() {
        if (e.v.isEmpty() && e.photos.isNullOrEmpty()) { st.say("Enter a measurement or add a photo"); return }
        e.v.firstNotNullOfOrNull { (k, v) -> measureProblem(k, v) }?.let { st.say(it); return }
        st.store.putMeasure(e); s.removed.forEach { st.platform.deletePhoto(it) }
        st.close(s); st.tab = Tab.Body; st.say("Measurements saved")
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(if (s.isNew) "Log measurements" else "Edit measurements", left = { IconBtn(Ic.x, "Close", { st.close(s) }) }, right = { Btn("Save", { save() }, kind = BtnKind.Primary, small = true) })
        Body {
            Field("Date (YYYY-MM-DD)", dateText, { t -> dateText = t; runCatching { LocalDate.parse(t.trim()) }.getOrNull()?.let { d -> s.e = s.e.copy(date = startOfDay(d) + 12 * 3_600_000L) } })
            MF.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (k, l, _) ->
                        val cur = e.v[k]?.let { r2(mShow(k, it, lb)) }
                        val ph = if (s.isNew) latest?.v?.get(k)?.let { plainN(r2(mShow(k, it, lb))) } ?: "" else ""
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Mu("$l (${mUnit(k, lb)})", Modifier.padding(bottom = 6.dp), size = 13.sp)
                            NumCell(cur, ph, { v -> s.e = s.e.copy(v = if (v == null) s.e.v - k else s.e.v + (k to mStore(k, v, lb))) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Label("Progress photos")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (e.photos ?: emptyList()).forEachIndexed { i, id ->
                    Box {
                        PhotoThumb(st, id, Modifier.size(96.dp, 128.dp))
                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                            .clickable { s.removed.add(id); s.e = s.e.copy(photos = (s.e.photos ?: emptyList()).filterIndexed { j, _ -> j != i }) }, contentAlignment = Alignment.Center) {
                            Icon(Ic.x, "Remove photo", Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
            }
            Btn("Add photos", {
                st.scope.launch {
                    s.status = "Adding…"
                    val ids = runCatching { st.platform.pickAndStorePhotos() }.getOrElse { emptyList() }
                    if (ids.isNotEmpty()) s.e = s.e.copy(photos = (s.e.photos ?: emptyList()) + ids)
                    s.status = if (ids.isEmpty()) "" else "${ids.size} photo${if (ids.size > 1) "s" else ""} added. Save to keep ${if (ids.size > 1) "them" else "it"}."
                }
            }, Modifier.padding(top = 10.dp), icon = Ic.photo)
            if (s.status.isNotEmpty()) Mu(s.status, Modifier.padding(top = 6.dp))
            Mu("Photos stay on this phone and aren't included in backups.", Modifier.padding(top = 6.dp), size = 13.sp)
            Btn("Save", { save() }, Modifier.fillMaxWidth().padding(top = 16.dp), kind = BtnKind.Primary)
            if (!s.isNew) Btn("Delete entry", {
                st.confirm("Delete this entry?", if (!e.photos.isNullOrEmpty()) "Its photos are deleted too." else null, "Delete entry") {
                    val old = st.data.measures[e.id]; st.store.deleteMeasure(e.id); st.close(s)
                    if (old != null) {
                        st.say("Entry deleted") { st.store.putMeasure(old) }
                        st.scope.launch { delay(7000); if (st.data.measures[old.id] == null) (old.photos ?: emptyList()).forEach { st.platform.deletePhoto(it) } }
                    }
                }
            }, Modifier.fillMaxWidth().padding(top = 8.dp), kind = BtnKind.Danger)
        }
    }
}

@Composable
fun PhotosScreen(st: AppState, s: Screen.Photos) {
    val list = st.idx.ms.flatMap { m -> (m.photos ?: emptyList()).map { it to m.date } }
    Column(Modifier.fillMaxSize()) {
        if (list.isEmpty()) { TopBar("Photos", left = { IconBtn(Ic.x, "Close", { st.close(s) }) }); return@Column }
        val i = s.i.coerceIn(0, list.size - 1); val a = list[i]; val b = list[0]; val two = s.cmp && i != 0
        TopBar(dLong(a.second), left = { IconBtn(Ic.x, "Close", { st.close(s) }) }, right = { Btn("Compare", { s.cmp = !s.cmp }, small = true, kind = if (s.cmp) BtnKind.Primary else BtnKind.Plain) })
        Body {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { PhotoThumb(st, a.first, Modifier.fillMaxWidth().aspectRatio(0.75f), crop = false); Mu(dShort(a.second)) }
                if (two) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { PhotoThumb(st, b.first, Modifier.fillMaxWidth().aspectRatio(0.75f), crop = false); Mu("Latest · ${dShort(b.second)}") }
            }
            if (s.cmp && i == 0) Mu("This is your latest photo. Go to an older one to compare.", Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("Older", { s.i = i + 1 }, enabled = i < list.size - 1, icon = Ic.left)
                Mu("${i + 1} of ${list.size}", Modifier.weight(1f).padding(horizontal = 8.dp))
                Btn("Newer", { s.i = i - 1 }, enabled = i > 0, icon = Ic.right)
            }
        }
    }
}

/* ---------- settings ---------- */
@Composable
fun SettingsScreenView(st: AppState) {
    val set = st.data.settings; val lb = LocalLb.current
    var key by remember { mutableStateOf(set.apiKey ?: "") }
    var model by remember { mutableStateOf(set.model ?: "") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", left = { IconBtn(Ic.x, "Close", { st.pop() }) })
        Body {
            val (hd, ht) = hevyProgress(st)
            BoxCard("Move from Hevy", if (hd == 0) "Bring your workouts, measurements and routines over from Hevy, step by step." else "$hd of $ht steps done.") {
                Btn(if (hd == 0) "Start" else if (hd == ht) "Review" else "Continue", { st.push(Screen.HevyGuide) }, kind = if (hd < ht) BtnKind.Primary else BtnKind.Plain)
            }
            BoxCard("Theme", "Dark is the default. System follows your phone's setting.") {
                Seg(listOf("dark" to "Dark", "light" to "Light", "system" to "System"), set.theme ?: "dark", { t -> st.setSettings { it.copy(theme = t) } })
            }
            BoxCard("Units", "Weights everywhere, including your history and bodyweight. Tape measurements use cm with kg and inches with lb.") {
                Seg(listOf("kg" to "kg", "lb" to "lb"), if (lb) "lb" else "kg", { u -> st.setSettings { it.copy(unit = u) } })
            }
            BoxCard("Weight suggestions", "Hints the next weight in each set from last session: hit your target reps (or the top of your rep range) and it suggests ${if (lb) "+5 lb" else "+2.5 kg"}; otherwise one more rep. A logged RPE above 8.5 holds the weight.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Toggle(set.sug != false) { st.setSettings { it.copy(sug = it.sug == false) } }
                    Btn("Rep range ${set.lo ?: 8}–${set.hi ?: 12}", {
                        st.showMenu("Rep range for suggestions", listOf(5 to 8, 6 to 10, 8 to 12, 10 to 15, 12 to 20).map { (lo, hi) -> MenuItem("$lo–$hi reps", on = lo == (set.lo ?: 8) && hi == (set.hi ?: 12)) { st.setSettings { it.copy(lo = lo, hi = hi) } } })
                    })
                }
            }
            BoxCard("Bodyweight in volume", "Counts your logged bodyweight in volume for pull-ups, dips and other bodyweight sets (bodyweight + added weight for weighted ones). Off matches Hevy.") {
                Toggle(set.bwv == true) { st.setSettings { it.copy(bwv = it.bwv != true) } }
            }
            BoxCard("Keep screen on", "Stops your phone sleeping while a workout is open.") { Toggle(set.awake != false) { st.setSettings { it.copy(awake = it.awake == false) } } }
            BoxCard("Default rest timer", "Used for exercises you haven't logged before.") {
                Btn(fmtRest(set.rest), { st.showMenu("Default rest timer", RESTS.map { r -> MenuItem(fmtRest(r), on = r == set.rest) { st.setSettings { it.copy(rest = r) } } }) }, icon = Ic.clock)
            }
            BoxCard("Import", "Hevy's workout export, its measurements export, or a Lift Log backup from the web app. Workouts also become routines (one per workout name used 3+ times in the last 4 months). Importing the same file twice doesn't create duplicates.") {
                Btn("Choose files", { st.importFiles() }, kind = BtnKind.Primary)
                if (st.importStatus.isNotEmpty()) Mu(st.importStatus, Modifier.padding(top = 8.dp))
            }
            BoxCard("Routines from history", "Turn each workout name you've used 3+ times in the last 4 months into a routine, built from its latest session.") {
                Btn("Build routines", { val n = st.buildRoutines(); if (n > 0) st.say("$n routine${if (n > 1) "s" else ""} added") })
            }
            BoxCard("Export", "Workouts use Hevy's CSV format, so they open in Hevy, Strong or any spreadsheet. The full backup restores here or in the Lift Log web app.") {
                FlowRowX { Btn("Workouts CSV", { st.export("w") }); Btn("Measurements CSV", { st.export("m") }); Btn("Full backup", { st.export("b") }) }
            }
            if (st.platform.coachEnabled) BoxCard("Coach", "The coach uses your own Anthropic API key, stored only on this phone. Get one at console.anthropic.com.") {
                Field("API key", key, { key = it }, placeholder = "sk-ant-…")
                Field("Model (optional)", model, { model = it }, placeholder = DEFAULT_MODEL)
                Btn("Save", { st.setSettings { it.copy(apiKey = key.trim().ifEmpty { null }, model = model.trim().ifEmpty { null }) }; st.say("Coach settings saved") }, small = true)
            }
            BoxCard("About", "Lift Log ${st.platform.appVersion}. Your workouts, routines and measurements are stored only on this phone.") {
                FlowRowX {
                    Btn("Privacy policy", { st.platform.openUrl(PRIVACY_URL) }, small = true)
                    Btn("Source code", { st.platform.openUrl(SOURCE_URL) }, small = true)
                }
            }
            Mu("${count(st.idx.ws.size, "workout")} · ${count(st.idx.rs.size, "routine")} · ${count(st.idx.ms.size, "measurement entry", "measurement entries")}. Everything is stored on this phone (${st.platform.name}).", Modifier.padding(top = 8.dp))
        }
    }
}

const val PRIVACY_URL = "https://dhammishivam.github.io/lift-log/privacy.html"
const val SOURCE_URL = "https://github.com/dhammishivam/lift-log"

/* ---------- plate calculator ---------- */
private val PCOL = mapOf(25.0 to 0xFFD6312B, 20.0 to 0xFF2A62D4, 15.0 to 0xFFE8B100, 10.0 to 0xFF2F9E44, 5.0 to 0xFFE9ECEF, 2.5 to 0xFF343A40, 1.25 to 0xFFADB5BD, 45.0 to 0xFF2A62D4, 35.0 to 0xFFE8B100)

@Composable
fun PlateScreen(st: AppState, s: Screen.Plate) {
    val lb = LocalLb.current; val p = LocalPal.current; val u = unitOf(lb)
    val bar = s.bar ?: BARS.getValue(u)[0]; val w = parseNum(s.w)
    Column(Modifier.fillMaxSize()) {
        TopBar("Plate calculator", left = { IconBtn(Ic.x, "Close", { st.close(s) }) })
        Body {
            Field("Total weight ($u)", s.w, { s.w = it }, keyboard = KeyboardType.Decimal)
            Mu("Bar", Modifier.padding(top = 4.dp, bottom = 6.dp))
            ChipRow { BARS.getValue(u).forEach { b -> Chip(if (b > 0) "${fmtN(b)} $u" else "No bar", bar == b) { s.bar = b } } }
            when {
                w == null -> Mu("Enter the total weight you want on the bar.", Modifier.padding(top = 12.dp))
                w < bar -> Mu("That's lighter than the bar (${fmtN(bar)} $u).", Modifier.padding(top = 12.dp))
                else -> {
                    val (out, left) = plates(w, bar, u); val big = PLATES.getValue(u)[0]
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.width(48.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(p.mu))
                        out.forEach { pl ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(22.dp).height((36 + pl / big * 96).dp).clip(RoundedCornerShape(4.dp)).background(Color(PCOL[pl] ?: 0xFF888888)))
                                Mu(fmtN(pl), size = 12.sp)
                            }
                        }
                        Box(Modifier.width(10.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(p.mu))
                    }
                    Txt("Each side: " + (if (out.isEmpty()) "no plates, just the bar" else out.joinToString(" + ") { fmtN(it) } + " $u"), weight = FontWeight.SemiBold)
                    if (left > 0) Txt("Can't load exactly ${fmtN(w)} $u with standard plates. Closest below: ${fmtN(r2(w - left))} $u.", Modifier.padding(top = 6.dp), size = 14.sp, color = p.wu)
                }
            }
        }
    }
}

/* ---------- 1RM calculator ---------- */
@Composable
fun OrmScreen(st: AppState) {
    val lb = LocalLb.current; val u = unitOf(lb)
    var w by remember { mutableStateOf("") }; var r by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("1RM calculator", left = { IconBtn(Ic.x, "Close", { st.pop() }) })
        Body {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Weight ($u)", w, { w = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                Field("Reps", r, { r = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            val wv = parseNum(w); val rv = parseNum(r)
            when {
                wv == null || rv == null || rv <= 0 -> Mu("Enter a weight and how many reps you did with it.")
                rv > 30 -> Mu("Estimates get unreliable past 30 reps.")
                else -> {
                    val (one, ep, br) = oneRm(wv, rv)
                    Big("${fmtN(r1(one))} $u", Modifier.padding(top = 8.dp), size = 34.sp)
                    Mu("Estimated one-rep max · average of Epley (${fmtN(r1(ep))}) and Brzycki (${fmtN(r1(br))})")
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) { Mu("% of 1RM", Modifier.weight(1f), size = 13.sp); Mu("Weight", Modifier.weight(1f), size = 13.sp); Mu("About", size = 13.sp) }
                    listOf(100, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50).forEach { pc ->
                        Divider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Txt("$pc%", Modifier.weight(1f), size = 15.sp); Txt("${fmtN(r1(one * pc / 100))} $u", Modifier.weight(1f), size = 15.sp)
                            Txt("${if (pc == 100) 1 else maxOf(1, kotlin.math.round(30 * (100.0 / pc - 1)).toInt())} reps", size = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

/* ---------- coach ---------- */
private fun md(t: String): AnnotatedString = buildAnnotatedString {
    t.lines().forEachIndexed { i, raw ->
        var l = raw.replace(Regex("^#+\\s*"), "")
        val bullet = Regex("^\\s*[-•*]\\s+").find(l)
        if (bullet != null) { append("•  "); l = l.substring(bullet.range.last + 1) }
        var rest = l
        while (true) {
            val a = rest.indexOf("**"); if (a < 0) { append(rest); break }
            val b = rest.indexOf("**", a + 2); if (b < 0) { append(rest); break }
            append(rest.substring(0, a)); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rest.substring(a + 2, b)) }; rest = rest.substring(b + 2)
        }
        if (i < t.lines().size - 1) append("\n")
    }
}

private val COACH_Q = listOf("How has my training gone this month?", "Which lifts have stalled, and what should I change?", "Am I training all my muscles evenly?", "Build me a 4-day upper/lower split from my history")

@Composable
fun CoachScreen(st: AppState) {
    val p = LocalPal.current
    val list = rememberLazyListState()
    LaunchedEffect(st.coachTurns.size, st.coachBusy) { val n = st.coachTurns.size + 2; list.animateScrollToItem(maxOf(0, n - 1)) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Coach", left = { IconBtn(Ic.x, "Close", { st.pop() }) }, right = {
            if (st.coachTurns.isNotEmpty() && !st.coachBusy) Btn("New chat", { st.coachTurns.clear() }, small = true) else Spacer(Modifier.width(44.dp))
        })
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(16.dp)) {
            item {
                if (st.data.settings.apiKey.isNullOrBlank()) Mu("Add your Anthropic API key in Settings → Coach first. It stays on this phone.", Modifier.padding(bottom = 10.dp), size = 15.sp)
                if (st.coachTurns.isEmpty()) {
                    Mu("Ask about your training. The coach reads your workouts, routines and measurements to answer, and can build routines for you.", size = 15.sp)
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { COACH_Q.forEach { q -> Chip(q, false) { st.sendCoach(q) } } }
                }
            }
            items(st.coachTurns.size) { i ->
                val t = st.coachTurns[i]; val user = t.role == "user"
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.fillMaxWidth(0.92f).wrapContentWidth(if (user) Alignment.End else Alignment.Start).clip(RoundedCornerShape(14.dp)).background(if (user) p.ac else p.s1).padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (user) Txt(t.content, size = 15.sp, color = Color.White) else Text(md(stripRoutines(t.content)), color = p.ink, fontSize = 15.sp, lineHeight = 22.sp)
                        t.routines?.let { rs ->
                            Btn(if (t.added) "Added" else "Add ${rs.size} routine${if (rs.size > 1) "s" else ""}: ${rs.joinToString(", ") { it.name }}", {
                                if (!t.added) { rs.forEach { st.store.putRoutine(it) }; st.coachTurns[i] = t.copy(added = true); st.say("${rs.size} routine${if (rs.size > 1) "s" else ""} added to your Workout tab") }
                            }, Modifier.padding(top = 8.dp), small = true, kind = if (t.added) BtnKind.Plain else BtnKind.Primary, enabled = !t.added)
                        }
                    }
                }
            }
            if (st.coachBusy) item { Box(Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(p.s1).padding(horizontal = 14.dp, vertical = 10.dp)) { Mu("Thinking…", size = 15.sp) } }
        }
        Divider()
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(st.coachInput, { st.coachInput = it }, Modifier.weight(1f), maxLines = 5, textStyle = TextStyle(color = p.ink, fontSize = 16.sp), cursorBrush = SolidColor(p.ac),
                decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(p.s1).padding(12.dp)) { if (st.coachInput.isEmpty()) Mu("Ask the coach", size = 16.sp); inner() } })
            if (st.coachBusy) Btn("Stop", { st.coachJob?.cancel() }) else Btn("Send", { st.sendCoach(st.coachInput) }, kind = BtnKind.Primary)
        }
    }
}
