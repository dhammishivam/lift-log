package app.liftlog.logic

import app.liftlog.model.CustomEx
import app.liftlog.model.ExItem
import app.liftlog.model.Routine
import app.liftlog.model.WSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Routine share links: https://dhammishivam.github.io/lift-log/r/#<payload>.
 * The routine travels in the part after #, so it never reaches a server. The page there shows it and offers
 * "Open in Lift Log" (liftlog://r?d=<payload>); the web app takes the link pasted into "Add from link".
 * The web app (web/index.html) encodes and decodes the same payload.
 */
const val SHARE_BASE = "https://dhammishivam.github.io/lift-log/r/"

/** A custom exercise travelling with a routine. */
@Serializable
data class SharedEx(val id: String, val name: String, val muscle: String = "Other", val equip: String = "Other", val type: String = "wr")

/** [n] name, [u] the sender's unit ("lb" or absent for kg), [e] exercises, [x] their names (for the web page), [c] custom exercises. */
@Serializable
data class SharedRoutine(
    val v: Int = 1,
    val n: String,
    val u: String? = null,
    val e: List<ExItem>,
    val x: List<String> = emptyList(),
    val c: List<SharedEx>? = null,
)

private val LinkJson = Json { explicitNulls = false; encodeDefaults = false; ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

@OptIn(ExperimentalEncodingApi::class)
private val B64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/** The link for [r]: targets only (no ticks or RPE), notes kept. */
@OptIn(ExperimentalEncodingApi::class)
fun routineLink(idx: Index, r: Routine, lb: Boolean): String {
    val items = r.exercises.map { it.copy(note = it.note?.ifBlank { null }, sets = it.sets.map { s -> WSet(t = s.t, kg = s.kg, reps = s.reps, km = s.km, sec = s.sec) }) }
    val exs = items.map { idx.exOf(it.eid) }
    val customs = exs.filter { it.custom }.distinctBy { it.id }.map { SharedEx(it.id, it.name, it.muscle, it.equip, it.type) }
    val sr = SharedRoutine(n = r.name, u = if (lb) "lb" else null, e = items, x = exs.map { it.name }, c = customs.ifEmpty { null })
    return SHARE_BASE + "#" + B64.encode(LinkJson.encodeToString(SharedRoutine.serializer(), sr).encodeToByteArray())
}

/** Finds a routine link in [text] (the whole share message, a bare link, or the app's liftlog://r?d= link). */
@OptIn(ExperimentalEncodingApi::class)
fun parseRoutineLink(text: String): SharedRoutine? = runCatching {
    val t = text.trim()
    val payload = Regex("""(?:/r/?#|[?&]d=)([A-Za-z0-9_-]{8,})""").find(t)?.groupValues?.get(1)
        ?: t.takeIf { Regex("^[A-Za-z0-9_-]{8,}$").matches(it) } ?: return null
    LinkJson.decodeFromString(SharedRoutine.serializer(), B64.decode(payload).decodeToString())
        .takeIf { it.n.isNotBlank() && it.e.isNotEmpty() }
}.getOrNull()

/**
 * Makes a shared routine ours. Exercises keep their library id; custom ones are matched by name to the
 * user's exercises, or created. Returns the routine and the custom exercises to add.
 */
fun adoptShared(idx: Index, s: SharedRoutine, newId: () -> String): Pair<Routine, List<CustomEx>> {
    val byName = idx.ex.values.associateBy { it.name.lowercase() }
    val made = mutableListOf<CustomEx>(); val map = HashMap<String, String>()
    fun idFor(name: String, muscle: String, equip: String, type: String): String =
        byName[name.lowercase()]?.id ?: made.firstOrNull { it.name.equals(name, true) }?.id
        ?: ("x_" + newId()).also { made += CustomEx(id = it, name = name, muscle = muscle, equip = equip, type = type) }
    s.c.orEmpty().forEach { c -> map[c.id] = idFor(c.name, c.muscle, c.equip, c.type) }
    val items = s.e.mapIndexed { i, it ->
        val eid = map[it.eid] ?: it.eid.takeIf { id -> idx.ex.containsKey(id) }
            ?: idFor(s.x.getOrNull(i) ?: "Shared exercise", "Other", "Other", "wr")
        it.copy(eid = eid, sets = it.sets.map { q -> q.copy(done = null, p = null, rpe = null) })
    }
    val name = s.n.trim().take(60)
    val taken = idx.rs.any { it.name.equals(name, true) }
    return Routine(id = "r_" + newId(), name = if (taken) "$name (shared)" else name, created = nowMs(), exercises = items) to made
}
