package app.liftlog.logic

import app.liftlog.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull

fun parseCsv(text: String): List<List<String>> {
    val t = text.removePrefix("﻿")
    val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val f = StringBuilder(); var q = false; var i = 0
    while (i < t.length) {
        val c = t[i]
        if (q) {
            if (c == '"') { if (i + 1 < t.length && t[i + 1] == '"') { f.append('"'); i++ } else q = false } else f.append(c)
        } else when (c) {
            '"' -> q = true
            ',' -> { row.add(f.toString()); f.clear() }
            '\n', '\r' -> { if (c == '\r' && i + 1 < t.length && t[i + 1] == '\n') i++; row.add(f.toString()); f.clear(); rows.add(row); row = mutableListOf() }
            else -> f.append(c)
        }
        i++
    }
    if (f.isNotEmpty() || row.isNotEmpty()) { row.add(f.toString()); rows.add(row) }
    return rows.filter { it.size > 1 || it.firstOrNull()?.isNotEmpty() == true }
}

fun hashStr(s: String): String {
    var h = 2166136261u
    for (ch in s) { h = h xor ch.code.toUInt(); h *= 16777619u }
    return h.toString(36)
}

/** [skipped] counts workouts already in Lift Log: the same Hevy row imported before, or one logged here and exported. */
data class HevyImport(val customs: List<CustomEx>, val workouts: List<Workout>, val skipped: Int = 0)

/** Same title and same start minute means the same workout (Hevy's export keeps minutes, not seconds). */
private fun sameWorkoutKey(title: String, start: Long) = title.trim().lowercase() + "|" + start / 60_000

/** Parses Hevy's workout CSV export. Known exercise names link to the library; others become custom exercises. */
fun parseHevyWorkouts(text: String, idx: Index): HevyImport {
    val rows = parseCsv(text)
    require(rows.size >= 2) { "The file is empty." }
    val h = rows[0].map { it.trim().lowercase() }
    fun c(n: String) = h.indexOf(n)
    val iT = c("title"); val iS = c("start_time"); val iE = c("end_time"); val iD = c("description"); val iX = c("exercise_title")
    val iN = c("exercise_notes"); val iST = c("set_type"); val iKg = c("weight_kg"); val iLb = c("weight_lbs"); val iR = c("reps")
    val iKm = c("distance_km"); val iMi = c("distance_miles"); val iSec = c("duration_seconds"); val iRpe = c("rpe"); val iSS = c("superset_id")
    require(iT >= 0 && iS >= 0 && iX >= 0) { "This doesn't look like a Hevy workout export." }
    val byName = HashMap<String, String>(); idx.ex.values.forEach { byName[it.name.lowercase()] = it.id }
    val newX = LinkedHashMap<String, CustomEx>()
    class WB(val key: String, val start: Long, val end: Long, val title: String, val notes: String) { val items = mutableListOf<ExItem>() }
    val ws = LinkedHashMap<String, WB>()
    val tm = mapOf("warmup" to "w", "dropset" to "d", "failure" to "f")
    rows.drop(1).forEach { r ->
        fun g(k: Int) = if (k >= 0) r.getOrNull(k)?.trim() ?: "" else ""
        val key = g(iT) + "|" + g(iS)
        val w = ws[key] ?: run {
            val st = parseDate(g(iS)) ?: return@forEach
            val en = parseDate(g(iE))
            WB(key, st, if (en != null && en > st) en else st + 3_600_000, g(iT).ifEmpty { "Workout" }, g(iD)).also { ws[key] = it }
        }
        val nm = g(iX); if (nm.isEmpty()) return@forEach
        val kg = parseNum(g(iKg)) ?: parseNum(g(iLb))?.let { r2(it * 0.45359237) }
        val km = parseNum(g(iKm)) ?: parseNum(g(iMi))?.let { r2(it * 1.609344) }
        val reps = parseNum(g(iR)); val sec = parseNum(g(iSec)); val rpe = parseNum(g(iRpe)); val ss = parseNum(g(iSS))?.toInt()
        var eid = byName[nm.lowercase()]
        if (eid == null) {
            val t = when { km != null && km > 0 -> "cd"; kg != null && kg > 0 -> "wr"; reps != null && reps > 0 -> "bw"; sec != null && sec > 0 -> "dur"; else -> "wr" }
            eid = "x_h" + hashStr(nm.lowercase())
            newX[eid] = CustomEx(id = eid, name = nm, type = t); byName[nm.lowercase()] = eid
        }
        val set = WSet(t = tm[g(iST).lowercase()] ?: "n", kg = kg, reps = reps, km = km, sec = sec, rpe = rpe)
        val last = w.items.lastOrNull()
        if (last == null || last.eid != eid) w.items.add(ExItem(eid = eid, note = g(iN), ss = ss, sets = listOf(set)))
        else w.items[w.items.size - 1] = last.copy(sets = last.sets + set)
    }
    val workouts = ws.values.filter { it.items.isNotEmpty() }.map { b ->
        Workout(id = "w_h" + hashStr(b.key), title = b.title, notes = b.notes, start = b.start, end = b.end, exercises = b.items, src = "hevy")
    }
    val have = idx.data.workouts.values.mapTo(HashSet()) { sameWorkoutKey(it.title, it.start) }
    val fresh = workouts.filter { it.id !in idx.data.workouts && sameWorkoutKey(it.title, it.start) !in have }
    val used = fresh.flatMapTo(HashSet()) { w -> w.exercises.map { it.eid } }
    return HevyImport(newX.values.filter { it.id !in idx.data.customs && it.id in used }, fresh, workouts.size - fresh.size)
}

/** Drops entries already in Lift Log: the same import again, or a day that already has an entry (an exported file coming back). */
fun newMeasures(ms: List<Measure>, idx: Index): List<Measure> {
    val days = idx.data.measures.values.mapTo(HashSet()) { hevyDate(it.date).substringBefore(",") }
    return ms.filter { it.id !in idx.data.measures && hevyDate(it.date).substringBefore(",") !in days }
}

fun parseHevyMeasures(text: String): List<Measure> {
    val rows = parseCsv(text)
    require(rows.isNotEmpty()) { "The file is empty." }
    val h = rows[0].map { it.trim().lowercase() }; val iD = h.indexOf("date")
    require(iD >= 0) { "This doesn't look like a Hevy measurements export." }
    return rows.drop(1).mapNotNull { r ->
        val ds = r.getOrNull(iD) ?: return@mapNotNull null
        val dt = parseDate(ds) ?: return@mapNotNull null
        val v = MF.mapNotNull { (k) -> val i = h.indexOf(k); val n = if (i >= 0) parseNum(r.getOrNull(i)) else null; n?.let { k to it } }.toMap()
        if (v.isEmpty()) null else Measure(id = "m_h" + hashStr(ds), date = dt, v = v, src = "hevy")
    }
}

private fun cq(v: Any?) = "\"" + (v?.toString() ?: "").replace("\"", "\"\"") + "\""
private fun nv(d: Double?) = d?.let { plainN(it) } ?: ""

/** Workouts in Hevy's CSV format (opens in Hevy, Strong or a spreadsheet). */
fun csvWorkouts(idx: Index): String {
    val tt = mapOf("n" to "normal", "w" to "warmup", "d" to "dropset", "f" to "failure")
    val sb = StringBuilder(listOf("title", "start_time", "end_time", "description", "exercise_title", "superset_id", "exercise_notes", "set_index", "set_type", "weight_kg", "reps", "distance_km", "duration_seconds", "rpe").joinToString(",") { cq(it) })
    idx.ws.forEach { w -> w.exercises.forEach { it2 -> it2.sets.forEachIndexed { i, s ->
        sb.append('\n').append(listOf(cq(w.title), cq(hevyDate(w.start)), cq(hevyDate(w.end)), cq(w.notes ?: ""), cq(idx.exOf(it2.eid).name), it2.ss?.toString() ?: "", cq(it2.note ?: ""), i.toString(), cq(tt[s.t] ?: "normal"), nv(s.kg), nv(s.reps), nv(s.km), nv(s.sec), nv(s.rpe)).joinToString(","))
    } } }
    return sb.toString()
}

fun csvMeasures(idx: Index): String {
    val sb = StringBuilder((listOf("date") + MF.map { it.first }).joinToString(",") { cq(it) })
    idx.ms.forEach { m -> sb.append('\n').append((listOf(cq(hevyDate(m.date))) + MF.map { (k) -> nv(m.v[k]) }).joinToString(",")) }
    return sb.toString()
}

/** Full backup in the web app's format: {"app":"liftlog","v":1,"docs":{id: doc}}. */
fun backupJson(d: Data): String {
    val docs = buildJsonObject {
        d.workouts.values.forEach { put(it.id, JsonX.encodeToJsonElement(Workout.serializer(), it)) }
        d.routines.values.forEach { put(it.id, JsonX.encodeToJsonElement(Routine.serializer(), it)) }
        d.customs.values.forEach { put(it.id, JsonX.encodeToJsonElement(CustomEx.serializer(), it)) }
        d.measures.values.forEach { put(it.id, JsonX.encodeToJsonElement(Measure.serializer(), it)) }
        put("settings", JsonX.encodeToJsonElement(Settings.serializer(), d.settings.copy(apiKey = null, model = null)))
        put("pins", JsonX.encodeToJsonElement(Pins.serializer(), d.pins))
    }
    return JsonX.encodeToString(JsonObject.serializer(), buildJsonObject { put("app", "liftlog"); put("v", 1); put("exported", hevyDate(nowMs())); put("docs", docs) })
}

/** Reads a backup (web or app). Returns the docs as (id, kind, element). */
fun parseBackup(text: String): List<Triple<String, String, JsonElement>> {
    val root = JsonX.parseToJsonElement(text).jsonObject
    val docs = root["docs"]?.jsonObject ?: throw IllegalArgumentException("No data found in that backup.")
    return docs.entries.mapNotNull { (id, el) ->
        val kind = runCatching { el.jsonObject["kind"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return@mapNotNull null
        if (kind in listOf("workout", "routine", "exercise", "settings", "measure", "pins")) Triple(id, kind, el) else null
    }
}
