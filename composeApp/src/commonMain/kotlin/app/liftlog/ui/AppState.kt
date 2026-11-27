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

/** Counts from one import, for the summary card on the Hevy guide. */
data class ImportSummary(val workouts: Int = 0, val measures: Int = 0, val routines: Int = 0, val exercises: Int = 0,
                         val skipped: Int = 0, val backup: Int = 0, val unknown: List<String> = emptyList())

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
    /** [fresh]: just saved from a live workout, so the page opens with a short summary. */
    class WorkoutDetail(val id: String, val fresh: Boolean = false) : Screen()
    class ExerciseDetail(val id: String) : Screen() { var k by mutableStateOf<String?>(null); var r by mutableStateOf("All"); var cmp by mutableStateOf<String?>(null) }
    /** Drag exercises into a new order; [onDone] gets the old indices in their new order. */
    class Reorder(val eids: List<String>, val onDone: (List<Int>) -> Unit) : Screen()
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
    /** "cal", "mus" or "rep", opened from the Profile dashboard. */
    data class Stats(val view: String) : Screen()
    /** Find a program: about you, days a week, suggestions, preview. */
    class Programs(p: Profile?, weight: String, height: String) : Screen() {
        var step by mutableStateOf(0); var gender by mutableStateOf(p?.gender); var height by mutableStateOf(height)
        var weight by mutableStateOf(weight); var exp by mutableStateOf(p?.exp); var equip by mutableStateOf(p?.equip); var goal by mutableStateOf(p?.goal)
        var days by mutableStateOf(p?.days); var pick by mutableStateOf<String?>(null)
    }
    class PinEditor(val eid: String, t: String) : Screen() { var t by mutableStateOf(t) }
    /** First open: name, units, goal and how to start. */
    object Welcome : Screen()
    /** A routine someone shared as a link, before it's added. */
    class SharedRoutineView(val r: SharedRoutine) : Screen()
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
    /** Index of the first exercise just added to the live workout, so the screen can bring it into view. */
    var focusEx by mutableStateOf<Int?>(null)
    var restTotal by mutableStateOf(1L)
    var timer by mutableStateOf<SetTimer?>(null)
    var celebration by mutableStateOf<Celebration?>(null)
    val celebrations: Boolean get() = data.settings.cele != false

    // tab view state
    var hv by mutableStateOf("list"); var hm by mutableStateOf("dur"); var wsel by mutableStateOf(11)
    var cm by mutableStateOf(0); var mu by mutableStateOf(30); var rp by mutableStateOf("month"); var ro by mutableStateOf(0)
    var exq by mutableStateOf(""); var exg by mutableStateOf("All"); var mk by mutableStateOf("weight_kg"); var mr by mutableStateOf("All")
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
        if (restEnd > 0 && now >= restEnd) { stopRest(); platform.restOver(); say("Rest over. Next set!") }
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
    /** The set after the last one ticked (or the first unticked set if everything after it is done). */
    private fun nextSet(a: Active): Pair<Int, Int>? {
        val flat = a.exercises.flatMapIndexed { ei, e -> e.sets.indices.map { ei to it } }
        fun done(p: Pair<Int, Int>) = a.exercises[p.first].sets[p.second].done == true
        val last = flat.indexOfLast { done(it) }
        return flat.drop(last + 1).firstOrNull { !done(it) } ?: flat.firstOrNull { !done(it) }
    }

    /** What the ongoing notification shows, or null when no new workout is running. */
    fun liveInfo(): LiveInfo? {
        val a = data.active ?: return null
        if (a.editId != null) return null
        val total = a.exercises.sumOf { it.sets.size }; val done = a.exercises.sumOf { e -> e.sets.count { it.done == true } }
        var exName: String? = null; var setLine: String? = null
        val next = nextSet(a)?.let { (ei, si) ->
            val it = a.exercises[ei]; val x = idx.exOf(it.eid); val T = typeOf(x.type)
            val pv = prevFor(idx.prevSets(it.eid, a.start, a.editId), it.sets, si); val sg = idx.sugFor(a, ei, si)
            var q = it.sets[si]
            T.f.forEach { f -> if (fieldVal(q, f, lb) == null) parseNum(phv(it, si, f, pv, sg, lb))?.let { v -> q = withField(q, f, v, lb) } }
            val vals = if (T.f.any { f -> fieldVal(q, f, lb) != null }) " · " + fmtSet(x.type, q, lb) else ""
            val lab = when (it.sets[si].t) { "w" -> "warm-up"; "d" -> "drop set"; "f" -> "failure set"; else -> "set ${setLabel(it.sets, si)}" }
            val working = it.sets.count { s -> s.t == "n" }
            exName = x.name
            setLine = when (it.sets[si].t) {
                "w" -> "Warm-up"; "d" -> "Drop set"; "f" -> "Failure set"
                else -> "Set ${setLabel(it.sets, si)}" + (if (working > 1) " of $working" else "")
            } + vals
            "${x.name} · $lab$vals"
        }
        val resting = restEnd.takeIf { it > 0 }
        return LiveInfo(a.title?.ifBlank { null } ?: defTitle(), a.start, resting, next, done, total, exName, setLine, resting?.let { restTotal })
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
            action.startsWith("route:") -> openRoutineLink(action.removePrefix("route:"))
            action.startsWith("start:") -> data.routines[action.removePrefix("start:")]?.let { r -> tab = Tab.Workout; startWorkout { fromTemplate(r.name, r.id, r.exercises) } }
        }
    }

    /** Opens a shared routine link (from a deep link or pasted text). */
    fun openRoutineLink(text: String): Boolean {
        val r = parseRoutineLink(text) ?: run { say("That isn't a Lift Log routine link"); return false }
        stack.removeAll { it is Screen.SharedRoutineView }; push(Screen.SharedRoutineView(r)); return true
    }

    fun shareRoutine(r: Routine) {
        val text = "Here's my “${r.name}” routine on Lift Log: ${routineLink(idx, r, lb)}"
        if (!platform.shareText(text, "Share routine")) say("Sharing isn't available here")
    }

    /** Adds a shared routine (and any custom exercises it needs); [start] also starts it. */
    fun addShared(s: Screen.SharedRoutineView, start: Boolean) {
        val (r, made) = adoptShared(idx, s.r) { rid() }
        made.forEach { store.putCustom(it) }
        store.putRoutine(r); close(s); tab = Tab.Workout
        if (start) startWorkout { fromTemplate(r.name, r.id, r.exercises) } else say("“${r.name}” added to your routines")
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
        val recs = idx.prm[id]?.n ?: 0
        if (a.editId == null && recs > 0 && celebrations) { celebration = Celebration("champ", count(recs, "new record") + "!", "${w.title} saved", Mascots.quip("champ")); platform.celebrate() }
        // A new workout opens with its own "Workout complete" summary, so only edits and celebrations-off need a toast.
        else if (a.editId != null) say("Workout updated")
        else if (recs > 0) say("Workout saved. 🏆 " + count(recs, "new record") + "!")
        push(Screen.WorkoutDetail(id, fresh = a.editId == null))
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

    fun importFiles() = scope.launch { importTexts(platform.pickTextFiles()) }

    /** Files shared or opened into the app (e.g. straight from Hevy's export): import them and show the result on the Hevy guide. */
    fun importIncoming(files: List<Pair<String, String>>) = scope.launch {
        if (files.isEmpty()) return@launch
        if (stack.lastOrNull() != Screen.HevyGuide) { stack.clear(); push(Screen.HevyGuide) }
        importTexts(files)
    }

    /** What the last import brought in, for the summary card. */
    var lastImport by mutableStateOf<ImportSummary?>(null)

    /** True while an import runs, for the full-screen loading overlay. */
    var importing by mutableStateOf(false)

    suspend fun importTexts(files: List<Pair<String, String>>) {
        if (files.isEmpty()) return
        val out = mutableListOf<String>(); var sum = ImportSummary()
        importStatus = "Importing…"; importing = true
        kotlinx.coroutines.yield() // let the overlay draw before the parsing starts
        try {
            files.forEach { (name, text) ->
                val head = text.lineSequence().firstOrNull()?.lowercase() ?: ""
                when {
                    // A Lift Log backup is one line of JSON that can mention "weight_kg", so check for it first.
                    text.trimStart().startsWith("{") -> {
                        val docs = parseBackup(text); store.importDocs(docs); out += "${docs.size} items from backup"; sum = sum.copy(backup = sum.backup + docs.size)
                    }
                    head.contains("exercise_title") -> {
                        importStatus = "Importing workouts…"
                        val ix = idx
                        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { parseHevyWorkouts(text, ix) } // keep the spinner moving
                        if (r.workouts.isEmpty() && r.skipped == 0) { out += "$name: no workouts found"; return@forEach }
                        sum = sum.copy(skipped = sum.skipped + r.skipped)
                        if (r.workouts.isEmpty()) { out += "no new workouts (${r.skipped} already in Lift Log)"; return@forEach }
                        store.importAll(r.customs, r.workouts, emptyList(), emptyList())
                        val nr = idx.routinesFromHistory().also { rs -> store.importAll(emptyList(), emptyList(), emptyList(), rs) }.size
                        sum = sum.copy(workouts = sum.workouts + r.workouts.size, exercises = sum.exercises + r.customs.size, routines = sum.routines + nr)
                        out += count(r.workouts.size, "new workout") + (if (r.customs.isNotEmpty()) ", " + count(r.customs.size, "new exercise") else "") +
                            (if (nr > 0) ", " + count(nr, "routine") else "") + (if (r.skipped > 0) " (${r.skipped} already in Lift Log)" else "")
                    }
                    head.contains("weight_kg") || head.contains("waist_in") -> {
                        val all = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { parseHevyMeasures(text) }; val ms = newMeasures(all, idx); store.importAll(emptyList(), emptyList(), ms, emptyList())
                        sum = sum.copy(measures = sum.measures + ms.size, skipped = sum.skipped + (all.size - ms.size))
                        out += count(ms.size, "new measurement entry", "new measurement entries") + (if (all.size > ms.size) " (${all.size - ms.size} already in Lift Log)" else "")
                    }
                    else -> { out += "$name: not a Hevy export or Lift Log backup"; sum = sum.copy(unknown = sum.unknown + name) }
                }
            }
            importStatus = "Done: ${out.joinToString("; ")}."
            lastImport = sum
            say("Import finished")
        } catch (e: Throwable) { importStatus = e.message ?: "Import failed." } finally { importing = false }
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
    return when { h < 4 || h >= 21 -> "Night workout"; h < 12 -> "Morning workout"; h < 17 -> "Afternoon workout"; else -> "Evening workout" }
}
