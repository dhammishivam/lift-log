package app.liftlog.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.liftlog.db.LiftDb
import app.liftlog.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/**
 * All data lives in memory as [Data] (Compose state) and is mirrored to SQLite as JSON documents,
 * one row per document, in the same shape the web app uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Store(platform: Platform, private val scope: CoroutineScope) {
    private val db = LiftDb(platform.sqlDriver())
    /** One writer at a time keeps writes to the same document in order. */
    private val io = Dispatchers.Default.limitedParallelism(1)
    var data by mutableStateOf(Data())
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        scope.launch {
            val rows = withContext(io) { db.docQueries.selectAll().executeAsList() }
            var d = Data()
            rows.forEach { r -> d = runCatching { applyJson(d, r.kind, JsonX.parseToJsonElement(r.body)) }.getOrDefault(d) }
            data = d; loaded = true
        }
    }

    private fun applyJson(d: Data, kind: String, el: JsonElement): Data = when (kind) {
        "workout" -> JsonX.decodeFromJsonElement(Workout.serializer(), el).let { d.copy(workouts = d.workouts + (it.id to it)) }
        "routine" -> JsonX.decodeFromJsonElement(Routine.serializer(), el).let { d.copy(routines = d.routines + (it.id to it)) }
        "exercise" -> JsonX.decodeFromJsonElement(CustomEx.serializer(), el).let { d.copy(customs = d.customs + (it.id to it)) }
        "measure" -> JsonX.decodeFromJsonElement(Measure.serializer(), el).let { d.copy(measures = d.measures + (it.id to it)) }
        "settings" -> d.copy(settings = JsonX.decodeFromJsonElement(Settings.serializer(), el).let { s -> s.copy(apiKey = s.apiKey ?: d.settings.apiKey, model = s.model ?: d.settings.model) })
        "pins" -> d.copy(pins = JsonX.decodeFromJsonElement(Pins.serializer(), el))
        "active" -> d.copy(active = JsonX.decodeFromJsonElement(Active.serializer(), el))
        else -> d
    }

    private fun write(id: String, kind: String, body: String?) {
        scope.launch(io) {
            if (body == null) db.docQueries.deleteById(id) else db.docQueries.upsert(id, kind, body)
        }
    }

    fun putWorkout(w: Workout) { data = data.copy(workouts = data.workouts + (w.id to w)); write(w.id, "workout", JsonX.encodeToString(Workout.serializer(), w)) }
    fun deleteWorkout(id: String) { data = data.copy(workouts = data.workouts - id); write(id, "workout", null) }
    fun putRoutine(r: Routine) { data = data.copy(routines = data.routines + (r.id to r)); write(r.id, "routine", JsonX.encodeToString(Routine.serializer(), r)) }
    fun deleteRoutine(id: String) { data = data.copy(routines = data.routines - id); write(id, "routine", null) }
    fun putCustom(x: CustomEx) { data = data.copy(customs = data.customs + (x.id to x)); write(x.id, "exercise", JsonX.encodeToString(CustomEx.serializer(), x)) }
    fun deleteCustom(id: String) { data = data.copy(customs = data.customs - id); write(id, "exercise", null) }
    fun putMeasure(m: Measure) { data = data.copy(measures = data.measures + (m.id to m)); write(m.id, "measure", JsonX.encodeToString(Measure.serializer(), m)) }
    fun deleteMeasure(id: String) { data = data.copy(measures = data.measures - id); write(id, "measure", null) }
    fun putSettings(s: Settings) { data = data.copy(settings = s); write("settings", "settings", JsonX.encodeToString(Settings.serializer(), s)) }
    fun putPins(p: Pins) { data = data.copy(pins = p); write("pins", "pins", JsonX.encodeToString(Pins.serializer(), p)) }

    private var activeJob: Job? = null
    /** The live workout changes on every keystroke: update memory now, write after a short pause. */
    fun setActive(a: Active?, immediate: Boolean = false) {
        data = data.copy(active = a)
        activeJob?.cancel()
        if (a == null || immediate) { write("active", "active", a?.let { JsonX.encodeToString(Active.serializer(), it) }); return }
        activeJob = scope.launch { delay(400); write("active", "active", JsonX.encodeToString(Active.serializer(), a)) }
    }
    fun flushActive() { data.active?.let { setActive(it, immediate = true) } }

    /** Bulk import (backup restore). */
    fun importDocs(docs: List<Triple<String, String, JsonElement>>) {
        var d = data
        docs.forEach { (id, kind, el) ->
            d = runCatching { applyJson(d, kind, el) }.getOrDefault(d)
            write(if (kind == "settings") "settings" else if (kind == "pins") "pins" else id, kind, el.toString())
        }
        data = d
    }
    fun importAll(customs: List<CustomEx>, workouts: List<Workout>, measures: List<Measure>, routines: List<Routine>) {
        data = data.copy(
            customs = data.customs + customs.associateBy { it.id },
            workouts = data.workouts + workouts.associateBy { it.id },
            measures = data.measures + measures.associateBy { it.id },
            routines = data.routines + routines.associateBy { it.id },
        )
        scope.launch(io) {
            db.transaction {
                customs.forEach { db.docQueries.upsert(it.id, "exercise", JsonX.encodeToString(CustomEx.serializer(), it)) }
                workouts.forEach { db.docQueries.upsert(it.id, "workout", JsonX.encodeToString(Workout.serializer(), it)) }
                measures.forEach { db.docQueries.upsert(it.id, "measure", JsonX.encodeToString(Measure.serializer(), it)) }
                routines.forEach { db.docQueries.upsert(it.id, "routine", JsonX.encodeToString(Routine.serializer(), it)) }
            }
        }
    }
}
