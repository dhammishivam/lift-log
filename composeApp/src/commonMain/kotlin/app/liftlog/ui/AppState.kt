package app.liftlog.ui

import androidx.compose.runtime.*
import app.liftlog.data.LiveInfo
import app.liftlog.data.Platform
import app.liftlog.data.Store
import app.liftlog.logic.*
import app.liftlog.model.*
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class Tab { Workout, History, Exercises, Body }

data class MenuItem(val label: String, val danger: Boolean = false, val on: Boolean = false, val action: () -> Unit)
data class MenuReq(val title: String?, val msg: String?, val items: List<MenuItem>)
data class ToastMsg(val text: String, val undo: (() -> Unit)?, val id: Long = nowMs())
data class SetTimer(val ei: Int, val si: Int, val start: Long, val tgt: Double)

/** Full-screen pages that slide over the tabs. Mutable fields are Compose state. */
sealed class Screen {
    object Live : Screen()
    class Finish(title: String, whenMs: Long, min: Int, notes: String) : Screen() {
        var title by mutableStateOf(title); var whenMs by mutableStateOf(whenMs); var min by mutableStateOf(min.toString())
        var notes by mutableStateOf(notes); var upd by mutableStateOf(false)
    }
    class WorkoutDetail(val id: String) : Screen()
    class ExerciseDetail(val id: String) : Screen() { var k by mutableStateOf<String?>(null); var r by mutableStateOf("All"); var cmp by mutableStateOf<String?>(null) }
    class Picker(val mode: String, val onDone: (List<String>) -> Unit) : Screen() { var q by mutableStateOf(""); var g by mutableStateOf("All"); val sel = mutableStateListOf<String>() }
    class ExEditor(val existing: CustomEx?, val prefill: String = "", val onSaved: ((String) -> Unit)? = null) : Screen()
    class RoutineEditor(draft: Routine, val isNew: Boolean) : Screen() { var r by mutableStateOf(draft); val start = nowMs() }
    class MeasureEditor(draft: Measure, val isNew: Boolean) : Screen() { var e by mutableStateOf(draft); val removed = mutableStateListOf<String>(); var status by mutableStateOf("") }
    class Photos(i: Int) : Screen() { var i by mutableStateOf(i); var cmp by mutableStateOf(false) }
    object SettingsScreen : Screen()
    class Plate(w: String) : Screen() { var w by mutableStateOf(w); var bar by mutableStateOf<Double?>(null) }
    object Orm : Screen()
    object Coach : Screen()
    object HevyGuide : Screen()
    class PinEditor(val eid: String, t: String) : Screen() { var t by mutableStateOf(t) }
}

class AppState(val platform: Platform, val store: Store, val scope: CoroutineScope) {
    private val idxState = derivedStateOf { Index(store.data) }
    val idx: Index get() = idxState.value
    val data: Data get() = store.data
    val lb: Boolean get() = store.data.settings.unit == "lb"

    var tab by mutableStateOf(Tab.Workout)
    val stack = mutableStateListOf<Screen>()
    var menu by mutableStateOf<MenuReq?>(null)
    var toast by mutableStateOf<ToastMsg?>(null)
    var now by mutableStateOf(nowMs())
    var restEnd by mutableStateOf(0L)
    var restTotal by mutableStateOf(1L)
    var timer by mutableStateOf<SetTimer?>(null)

    // tab view state
    var hv by mutableStateOf("list"); var hm by mutableStateOf("dur"); var wsel by mutableStateOf(11)
    var cm by mutableStateOf(0); var mu by mutableStateOf(30); var rp by mutableStateOf("month"); var ro by mutableStateOf(0)
    var exq by mutableStateOf(""); var mk by mutableStateOf("weight_kg"); var mr by mutableStateOf("All")
    val fold = mutableStateMapOf<String, Boolean>()
    var importStatus by mutableStateOf("")

    // coach
    val coachTurns = mutableStateListOf<ChatTurn>()
    var coachBusy by mutableStateOf(false)
    var coachInput by mutableStateOf("")
    var coachJob: Job? = null
    val http: HttpClient by lazy { HttpClient() }

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun close(s: Screen) { stack.remove(s) }
    fun liveOpen() = stack.any { it is Screen.Live }

    fun showMenu(title: String?, items: List<MenuItem>, msg: String? = null) { menu = MenuReq(title, msg, items) }
    fun confirm(title: String, msg: String?, ok: String, onOk: () -> Unit) = showMenu(title, listOf(MenuItem(ok, danger = true, action = onOk)), msg)
    fun say(text: String, undo: (() -> Unit)? = null) { toast = ToastMsg(text, undo) }

    fun setSettings(f: (Settings) -> Settings) = store.putSettings(f(store.data.settings))

    /* ---------- live workout ---------- */
    fun startWorkout(make: () -> Active) {
        val a = data.active
        if (a != null) {
            showMenu(if (a.editId != null) "You are editing a workout" else "You have a workout in progress", listOf(
                MenuItem(if (a.editId != null) "Go back to it" else "Resume it") { openLive() },
                MenuItem(if (a.editId != null) "Drop the edit and start new" else "Discard it and start new", danger = true) { begin(make()) },
            ))
        } else begin(make())
    }
    private fun begin(a: Active) { store.setActive(a, immediate = true); stopRest(); timer = null; openLive() }
    fun openLive() { if (!liveOpen()) push(Screen.Live) }
    fun minimize() { store.flushActive(); stack.removeAll { it is Screen.Live } }
    fun updateActive(f: (Active) -> Active) { data.active?.let { store.setActive(f(it)) } }
    fun setItems(items: List<ExItem>) = updateActive { it.copy(exercises = items) }

    fun startRest(sec: Int?) {
        if (sec == null || sec <= 0) return
        restTotal = sec * 1000L; restEnd = nowMs() + restTotal; platform.setRestEnd(restEnd)
    }
    fun stopRest() { restEnd = 0; platform.setRestEnd(null) }
    fun shiftRest(ms: Long) {
        if (restEnd == 0L) return
        restEnd += ms; if (ms > 0) restTotal += ms
        if (restEnd <= nowMs()) stopRest() else platform.setRestEnd(restEnd)
    }

    fun tick() {
        now = nowMs()
        if (restEnd > 0 && now >= restEnd) { stopRest(); platform.alert(); say("Rest over. Next set!") }
        val t = timer
        if (t != null && t.tgt > 0 && now - t.start >= t.tgt * 1000) { platform.alert(); finishTimer(true) }
    }

    fun finishTimer(full: Boolean) {
        val t = timer ?: return; timer = null
        val a = data.active ?: return
        val it = a.exercises.getOrNull(t.ei) ?: return
        val s = it.sets.getOrNull(t.si) ?: return
        val sec = if (full) t.tgt else maxOf(1.0, ((nowMs() - t.start) / 1000.0).let { kotlin.math.round(it) })
        val sets = it.sets.toMutableList(); sets[t.si] = s.copy(sec = sec, done = true)
        setItems(a.exercises.toMutableList().also { l -> l[t.ei] = it.copy(sets = sets) })
        if (a.editId == null) startRest(it.rest)
    }

    /* ---------- notification and widget ---------- */
    private fun nextSet(a: Active): Pair<Int, Int>? {
        a.exercises.forEachIndexed { ei, e -> e.sets.forEachIndexed { si, s -> if (s.done != true) return ei to si } }
        return null
    }

    /** What the ongoing notification shows, or null when no new workout is running. */
    fun liveInfo(): LiveInfo? {
        val a = data.active ?: return null
        if (a.editId != null) return null
        val total = a.exercises.sumOf { it.sets.size }; val done = a.exercises.sumOf { e -> e.sets.count { it.done == true } }
        val next = nextSet(a)?.let { (ei, si) ->
            val it = a.exercises[ei]; val x = idx.exOf(it.eid); val T = typeOf(x.type)
            val pv = prevFor(idx.prevSets(it.eid, a.start, a.editId), it.sets, si); val sg = idx.sugFor(a, ei, si)
            var q = it.sets[si]
            T.f.forEach { f -> if (fieldVal(q, f, lb) == null) parseNum(phv(it, si, f, pv, sg, lb))?.let { v -> q = withField(q, f, v, lb) } }
            val vals = if (T.f.any { f -> fieldVal(q, f, lb) != null }) " · " + fmtSet(x.type, q, lb) else ""
            "${x.name} · set ${setLabel(it.sets, si)}$vals"
        }
        return LiveInfo(a.title?.ifBlank { null } ?: defTitle(), a.start, restEnd.takeIf { it > 0 }, next, done, total)
    }

    /** Taps from the notification and the widget. */
    fun handleAction(action: String) {
        when {
            action == "minus" -> shiftRest(-15_000)
            action == "plus" -> shiftRest(15_000)
            action == "skip" -> stopRest()
            action == "done" -> data.active?.let { a -> nextSet(a)?.let { (ei, si) -> checkSet(this, ei, si) } }
            action == "open" -> if (data.active != null) openLive()
            action == "empty" -> { tab = Tab.Workout; startWorkout { Active(id = "w_" + rid(), start = nowMs(), title = "") } }
            action.startsWith("start:") -> data.routines[action.removePrefix("start:")]?.let { r -> tab = Tab.Workout; startWorkout { fromTemplate(r.name, r.id, r.exercises) } }
        }
    }

    fun shareWorkout(w: Workout, dark: Boolean) {
        if (!platform.shareCard(shareCard(idx, w, lb, dark))) say("Sharing isn't available here")
    }

    fun discardActive() { store.setActive(null); stopRest(); timer = null; stack.removeAll { it is Screen.Live || it is Screen.Finish } }

    fun saveWorkout(f: Screen.Finish) {
        val a = data.active ?: return
        val exs = normSS(a.exercises.map { it2 ->
            it2.copy(note = it2.note ?: "", rest = it2.rest ?: 0, sets = it2.sets.filter { it.done == true }.map { s -> WSet(t = s.t, kg = s.kg, reps = s.reps, km = s.km, sec = s.sec, rpe = s.rpe) })
        }.filter { it.sets.isNotEmpty() })
        if (exs.isEmpty()) { say("Complete at least one set first"); return }
        val start = f.whenMs; val min = (parseNum(f.min) ?: 1.0).coerceAtLeast(1.0)
        val id = a.editId ?: a.id
        val w = Workout(id = id, title = f.title.trim().ifEmpty { defTitle() }, notes = f.notes, start = start, end = start + (min * 60000).toLong(), routineId = a.routineId, exercises = exs)
        store.putWorkout(w)
        if (f.upd && a.routineId != null) data.routines[a.routineId]?.let { r ->
            store.putRoutine(r.copy(updated = nowMs(), exercises = exs.map { it.copy(note = "", sets = it.sets.map { s -> s.copy(rpe = null) }) }))
        }
        store.setActive(null); stopRest(); timer = null
        stack.clear(); tab = Tab.History; hv = "list"; wsel = 11
        val cmp = compareWeight(idx.wVol(w)); val recs = idx.prm[id]?.n ?: 0
        say(when {
            a.editId != null -> "Workout updated"
            recs > 0 -> "Workout saved. 🏆 " + count(recs, "new record") + "!"
            else -> "Workout saved. ${cmp.ifEmpty { "Nice work!" }}"
        })
        push(Screen.WorkoutDetail(id))
    }

    fun fromTemplate(title: String, routineId: String?, exs: List<ExItem>) = Active(
        id = "w_" + rid(), start = nowMs(), title = title, routineId = routineId,
        exercises = exs.map { it2 -> ExItem(it2.eid, "", it2.rest ?: idx.lastRest(it2.eid), it2.ss, it2.sets.map { s -> WSet(t = s.t, p = SetVals(s.kg, s.reps, s.km, s.sec)) }) },
    )
    fun blankItem(eid: String): ExItem {
        val p = idx.prevSets(eid, nowMs(), null)
        return ExItem(eid, "", idx.lastRest(eid), null, p?.map { WSet(t = it.t) } ?: listOf(WSet()))
    }

    /* ---------- other actions ---------- */
    fun buildRoutines(): Int {
        val rs = idx.routinesFromHistory()
        if (rs.isEmpty()) { say("No new routines to build: recent workout names already have one"); return 0 }
        rs.forEach { store.putRoutine(it) }; return rs.size
    }

    fun savePin(eid: String, t: String) {
        val n = data.pins.n.toMutableMap(); val v = t.trim()
        if (v.isEmpty()) n.remove(eid) else n[eid] = v
        store.putPins(Pins(n = n))
    }

    fun importFiles() = scope.launch {
        val files = platform.pickTextFiles(); if (files.isEmpty()) return@launch
        val out = mutableListOf<String>()
        try {
            files.forEach { (name, text) ->
                val head = text.lineSequence().firstOrNull()?.lowercase() ?: ""
                when {
                    head.contains("exercise_title") -> {
                        importStatus = "Importing workouts…"
                        val r = parseHevyWorkouts(text, idx)
                        if (r.workouts.isEmpty() && r.skipped == 0) { out += "$name: no workouts found"; return@forEach }
                        if (r.workouts.isEmpty()) { out += "no new workouts (${r.skipped} already in Lift Log)"; return@forEach }
                        store.importAll(r.customs, r.workouts, emptyList(), emptyList())
                        val nr = idx.routinesFromHistory().also { rs -> store.importAll(emptyList(), emptyList(), emptyList(), rs) }.size
                        out += count(r.workouts.size, "new workout") + (if (r.customs.isNotEmpty()) ", " + count(r.customs.size, "new exercise") else "") +
                            (if (nr > 0) ", " + count(nr, "routine") else "") + (if (r.skipped > 0) " (${r.skipped} already in Lift Log)" else "")
                    }
                    head.contains("weight_kg") || head.contains("waist_in") -> {
                        val all = parseHevyMeasures(text); val ms = newMeasures(all, idx); store.importAll(emptyList(), emptyList(), ms, emptyList())
                        out += count(ms.size, "new measurement entry", "new measurement entries") + (if (all.size > ms.size) " (${all.size - ms.size} already in Lift Log)" else "")
                    }
                    text.trimStart().startsWith("{") -> {
                        val docs = parseBackup(text); store.importDocs(docs); out += "${docs.size} items from backup"
                    }
                    else -> out += "$name: not a Hevy export or Lift Log backup"
                }
            }
            importStatus = "Done: ${out.joinToString("; ")}."
            say("Import finished")
        } catch (e: Throwable) { importStatus = e.message ?: "Import failed." }
    }

    fun export(kind: String) = scope.launch {
        val stamp = hevyDate(nowMs()).substringBefore(",").replace(' ', '-')
        val ok = when (kind) {
            "w" -> if (idx.ws.isEmpty()) { say("No workouts to export yet"); return@launch } else platform.saveText("liftlog-workouts-$stamp.csv", "text/csv", csvWorkouts(idx))
            "m" -> if (idx.ms.isEmpty()) { say("No measurements to export yet"); return@launch } else platform.saveText("liftlog-measurements-$stamp.csv", "text/csv", csvMeasures(idx))
            else -> platform.saveText("liftlog-backup-$stamp.json", "application/json", backupJson(data))
        }
        if (ok) say("Exported")
    }

    fun sendCoach(q: String) {
        val text = q.trim(); if (text.isEmpty() || coachBusy) return
        val key = data.settings.apiKey
        coachTurns.add(ChatTurn("user", text)); coachInput = ""
        if (key.isNullOrBlank()) { coachTurns.add(ChatTurn("assistant", "Add your Anthropic API key in Settings → Coach to use the coach. It stays on this phone.", err = true)); return }
        coachBusy = true
        coachJob = scope.launch {
            try {
                val ans = askCoach(http, key, data.settings.model?.ifBlank { null } ?: DEFAULT_MODEL, coachSystem(idx), coachTurns.toList())
                coachTurns.add(ChatTurn("assistant", ans, routines = parseRoutines(ans, idx)))
            } catch (e: kotlinx.coroutines.CancellationException) {
                coachTurns.add(ChatTurn("assistant", "(stopped)", err = true)); throw e
            } catch (e: Throwable) {
                coachTurns.add(ChatTurn("assistant", e.message ?: "Something went wrong. Try again.", err = true))
            } finally { coachBusy = false }
        }
    }
}

fun defTitle(): String {
    val h = ldt(nowMs()).hour
    return if (h < 12) "Morning workout" else if (h < 17) "Afternoon workout" else "Evening workout"
}
