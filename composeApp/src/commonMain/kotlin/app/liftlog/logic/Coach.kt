package app.liftlog.logic

import app.liftlog.model.*
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

const val DEFAULT_MODEL = "claude-sonnet-5-5"

data class ChatTurn(val role: String, val content: String, val routines: List<Routine>? = null, val added: Boolean = false, val err: Boolean = false)

/** The coach's view of the user's training, as plain text. */
fun coachData(idx: Index): String {
    val lb = idx.lb; val now = nowMs(); val (cur, best) = idx.weekRuns(); val L = mutableListOf<String>()
    L += "Today: ${dLong(now)}. Weights in ${unitOf(lb)}. Workouts logged: ${idx.ws.size}" +
        (if (idx.ws.isNotEmpty()) " (first ${dShort(idx.ws.last().start)}, latest ${dShort(idx.ws.first().start)})" else "") +
        ". Weekly streak $cur weeks, longest $best. Weekly goal: ${idx.settings.goal ?: "not set"}."
    val w0 = weekStart(now)
    L += "Workouts per week, oldest to newest (last 12 weeks): " + (0 until 12).joinToString(", ") { i ->
        val a = addDays(w0, -(11 - i) * 7); val b = addDays(a, 7); idx.ws.count { it.start in a until b }.toString()
    }
    L += "\nLAST 30 WORKOUTS (newest first, working sets only):"
    idx.ws.take(30).forEach { w ->
        L += "- ${dShort(w.start)} \"${w.title}\" ${dur(w.end - w.start)}: " + w.exercises.joinToString("; ") { it2 ->
            val t = idx.exOf(it2.eid).type; "${idx.exOf(it2.eid).name} [${working(it2.sets).joinToString(", ") { fmtSet(t, it, lb) }}]"
        }
    }
    L += "\nEXERCISE TRENDS (last 120 days):"
    val d120 = now - 120L * 86_400_000; val d30 = now - 30L * 86_400_000
    idx.h.entries.map { it.key to it.value.filter { e -> e.start >= d120 } }.filter { it.second.isNotEmpty() }.sortedByDescending { it.second.size }.take(30).forEach { (e, h) ->
        val t = idx.exOf(e).type
        fun bo(l: List<HEntry>) = l.maxOfOrNull { x -> working(x.sets).maxOfOrNull { setM(it).orm } ?: 0.0 } ?: 0.0
        L += "- ${idx.exOf(e).name}: ${h.size} sessions; last ${dShort(h[0].start)} [${working(h[0].sets).joinToString(", ") { fmtSet(t, it, lb) }}]" +
            (if (isVol(t)) "; best est. 1RM last 30 days ${fmtN(toU(bo(h.filter { it.start >= d30 }), lb))}, days 31-120 ${fmtN(toU(bo(h.filter { it.start < d30 }), lb))}" else "") +
            (if (idx.stalled(e)) "; STALLED 4 weeks" else "")
    }
    val cnt = HashMap<String, Int>()
    idx.ws.filter { it.start >= d30 }.forEach { w -> w.exercises.forEach { it2 -> val m = idx.exOf(it2.eid).muscle; cnt[m] = (cnt[m] ?: 0) + working(it2.sets).size } }
    L += "\nWORKING SETS PER MUSCLE, LAST 30 DAYS: " + (cnt.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "none" })
    L += "\nROUTINES:" + (if (idx.rs.isEmpty()) " none" else "")
    idx.rs.forEach { r -> L += "- ${r.name}${r.folder?.let { " [$it]" } ?: ""}: " + r.exercises.joinToString("; ") { "${idx.exOf(it.eid).name} ${it.sets.size} sets" } }
    L += "\nBODYWEIGHT (newest first): " + (idx.ms.filter { it.v["weight_kg"] != null }.take(8).joinToString(", ") { "${dShort(it.date)} ${wt(it.v["weight_kg"], lb)}" }.ifEmpty { "none logged" })
    L += "\nEXERCISE LIBRARY (use these exact names): " + idx.ex.values.joinToString("; ") { it.name }
    return L.joinToString("\n")
}

fun coachSystem(idx: Index): String = """You are the coach inside Lift Log, the user's personal workout tracker. Answer their question from their training data below. Be specific and practical: cite their actual exercises, numbers and dates. Keep answers under 250 words unless they ask for a plan. Plain text: short paragraphs or lines starting with "- ", **bold** allowed, no tables or headings. You are not a medical professional: for pain or injury, tell them to see one.
If, and only if, the user asks you to create or change routines, end your answer with one fenced block in exactly this form (a JSON array):
```routines
[{"name":"Push A","folder":"Coach","exercises":[{"exercise":"Bench Press (Barbell)","rest":120,"sets":[{"type":"warmup","weight":40,"reps":10},{"type":"normal","weight":70,"reps":8}]}]}]
```
Use only exercise names from the EXERCISE LIBRARY, spelled exactly. Weights are in ${unitOf(idx.lb)}; leave "weight" out for bodyweight or cardio exercises. Set types: normal, warmup, drop, failure.

TRAINING DATA
${coachData(idx)}"""

fun stripRoutines(t: String): String = t.replace(Regex("```routines[\\s\\S]*?(```|$)"), "").trim()

fun parseRoutines(t: String, idx: Index): List<Routine>? {
    val m = Regex("```routines\\s*([\\s\\S]*?)```").find(t) ?: return null
    val arr = runCatching { JsonX.parseToJsonElement(m.groupValues[1]).jsonArray }.getOrNull() ?: return null
    val byName = idx.ex.values.associateBy({ it.name.lowercase() }, { it.id })
    val ty = mapOf("warmup" to "w", "drop" to "d", "dropset" to "d", "failure" to "f")
    val base = nowMs()
    val out = arr.mapIndexedNotNull { i, el ->
        val r = el as? JsonObject ?: return@mapIndexedNotNull null
        fun s(o: JsonObject, k: String) = runCatching { o[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
        val items = (r["exercises"] as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val eid = byName[(s(o, "exercise") ?: "").lowercase()] ?: return@mapNotNull null
            val sets = (o["sets"] as? JsonArray)?.take(12)?.mapNotNull { q ->
                val so = q as? JsonObject ?: return@mapNotNull null
                WSet(t = ty[(s(so, "type") ?: "").lowercase()] ?: "n", kg = parseNum(s(so, "weight"))?.let { fromU(it, idx.lb) }, reps = parseNum(s(so, "reps")))
            } ?: emptyList()
            if (sets.isEmpty()) null else ExItem(eid = eid, rest = parseNum(s(o, "rest"))?.toInt() ?: idx.lastRest(eid), note = "", sets = sets)
        } ?: emptyList()
        if (items.isEmpty()) null else Routine(id = "r_" + rid(), name = (s(r, "name") ?: "Coach routine").take(60), folder = (s(r, "folder") ?: "Coach").take(40), created = base + i, updated = base, exercises = items)
    }
    return out.ifEmpty { null }
}

/** Calls the Anthropic Messages API with the user's own key. */
suspend fun askCoach(client: HttpClient, key: String, model: String, system: String, turns: List<ChatTurn>): String {
    val body = buildJsonObject {
        put("model", model)
        put("max_tokens", 2000)
        put("system", system)
        putJsonArray("messages") {
            turns.filter { !it.err }.takeLast(12).forEach { t -> addJsonObject { put("role", t.role); put("content", t.content) } }
        }
    }
    val res = client.post("https://api.anthropic.com/v1/messages") {
        header("x-api-key", key)
        header("anthropic-version", "2023-06-01")
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }
    val text = res.bodyAsText()
    val obj = runCatching { JsonX.parseToJsonElement(text).jsonObject }.getOrNull()
    if (res.status.value !in 200..299) {
        val msg = obj?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        throw IllegalStateException(when (res.status.value) {
            401 -> "Your API key was rejected. Check it in Settings."
            429 -> "Rate limited by the API. Try again in a moment."
            else -> msg ?: "The API returned ${res.status.value}."
        })
    }
    return obj?.get("content")?.jsonArray?.mapNotNull { c -> c.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("") ?: ""
}
