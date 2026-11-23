package app.liftlog.logic

import app.liftlog.model.*
import kotlin.math.round
import kotlin.math.roundToInt

/* ---------- tape measurements: stored in inches (Hevy's format), shown in cm with kg ---------- */

const val CM_PER_IN = 2.54

/** Unit a measurement is shown in: kg/lb for weight, % for body fat, cm (with kg) or in (with lb) for tape. */
fun mUnit(k: String, lb: Boolean): String = when {
    k == "weight_kg" -> unitOf(lb)
    k.endsWith("_in") -> if (lb) "in" else "cm"
    else -> MF.firstOrNull { it.first == k }?.third ?: ""
}

/** Stored value → the number shown on screen. */
fun mShow(k: String, v: Double, lb: Boolean): Double = when {
    k == "weight_kg" -> toU(v, lb)
    k.endsWith("_in") && !lb -> v * CM_PER_IN
    else -> v
}

/** Number typed on screen → stored value. */
fun mStore(k: String, v: Double, lb: Boolean): Double = when {
    k == "weight_kg" -> r2(fromU(v, lb))
    k.endsWith("_in") && !lb -> round(v / CM_PER_IN * 1000) / 1000
    else -> v
}

/** "78.5 kg", "18%", "81.3 cm". */
fun mFmt(k: String, v: Double, lb: Boolean): String {
    val u = mUnit(k, lb); val n = fmtN(r1(mShow(k, v, lb)))
    return if (u == "%") "$n%" else "$n $u"
}

/* ---------- last session's set for each row ---------- */

/** Warm-ups line up with last session's warm-ups and working sets with working sets, each by position. */
fun prevFor(prev: List<WSet>?, sets: List<WSet>, si: Int): WSet? {
    if (prev == null) return null
    val s = sets.getOrNull(si) ?: return null
    val w = s.t == "w"; val k = sets.take(si).count { (it.t == "w") == w }
    return prev.filter { (it.t == "w") == w }.getOrNull(k)
}

/* ---------- warm-up sets ---------- */

/**
 * Warm-up sets ramping to [work] (in the user's unit): the empty bar for 10 (barbell only, when the working
 * weight is well above it), then about 50 % × 8, 70 % × 5 and 85 % × 3, each rounded to a loadable step.
 * Returns (weight, reps) pairs in the user's unit, lightest first.
 */
fun warmupPlan(work: Double, bar: Double, lb: Boolean): List<Pair<Double, Double>> {
    val step = if (lb) 5.0 else 2.5
    fun rnd(v: Double) = (v / step).roundToInt() * step
    val out = mutableListOf<Pair<Double, Double>>()
    if (bar > 0 && work > bar * 1.6) out += bar to 10.0
    listOf(0.5 to 8.0, 0.7 to 5.0, 0.85 to 3.0).forEach { (pc, r) ->
        val q = maxOf(rnd(work * pc), bar)
        if (q > 0 && q < work && q > (out.lastOrNull()?.first ?: 0.0)) out += q to r
    }
    return out
}

/* ---------- records ---------- */

/** One record line: exercise name and what was beaten, e.g. "Bench Press (Barbell)" to "Heaviest weight 62.5 kg". */
data class RecordLine(val exercise: String, val what: String)

/** A record value as shown: "62.5 kg", "12 reps", "1:30". */
fun prValue(k: String, s: WSet, lb: Boolean): String {
    val m = setM(s)
    return when (k) {
        "w" -> wt(m.w, lb); "orm" -> wt(r1(m.orm), lb); "vol" -> wv(m.vol, lb)
        "reps" -> "${fmtN(m.reps)} reps"; "sec" -> fmtSec(m.sec); "km" -> "${fmtN(m.km)} km"
        else -> ""
    }
}

/** Records set in a saved workout, in the order they were logged. */
fun workoutRecords(idx: Index, w: Workout, lb: Boolean): List<RecordLine> {
    val pm = idx.prm[w.id] ?: return emptyList()
    return w.exercises.flatMapIndexed { ei, it ->
        it.sets.indices.flatMap { si -> pm.m["$ei:$si"]?.map { k -> RecordLine(idx.exOf(it.eid).name, "${PRL[k] ?: k} ${prValue(k, it.sets[si], lb)}") } ?: emptyList() }
    }
}

/** Records the live workout has set so far. */
fun liveRecords(idx: Index, a: Active, lb: Boolean): List<RecordLine> =
    a.exercises.flatMapIndexed { ei, it ->
        idx.livePrs(a, ei).entries.sortedBy { e -> e.key }.flatMap { (si, ks) -> ks.map { k -> RecordLine(idx.exOf(it.eid).name, "${PRL[k] ?: k} ${prValue(k, it.sets[si], lb)}") } }
    }

/* ---------- share card ---------- */

data class ShareLine(val name: String, val detail: String)
data class ShareCard(
    val title: String, val date: String, val duration: String, val volume: String, val sets: String,
    val records: List<RecordLine>, val lines: List<ShareLine>, val more: Int, val compare: String, val dark: Boolean,
)

/** What goes on a workout's share image: stats, up to 7 exercises with their best set, and any records. */
fun shareCard(idx: Index, w: Workout, lb: Boolean, dark: Boolean): ShareCard {
    val lines = w.exercises.map { it ->
        val x = idx.exOf(it.eid); val work = working(it.sets).ifEmpty { it.sets }
        val best = work.maxByOrNull { s -> setM(s).let { m -> if (isVol(x.type)) m.orm * 1000 + m.vol else m.reps + m.sec + m.km } }
        ShareLine(x.name, count(it.sets.size, "set") + (best?.let { b -> " · best " + fmtSet(x.type, b, lb) } ?: ""))
    }
    return ShareCard(
        title = w.title, date = "${dLong(w.start)} · ${tShort(w.start)}", duration = dur(w.end - w.start),
        volume = wv(idx.wVol(w), lb), sets = idx.wSets(w).toString(), records = workoutRecords(idx, w, lb),
        lines = lines.take(7), more = (lines.size - 7).coerceAtLeast(0), compare = compareWeight(idx.wVol(w)), dark = dark,
    )
}

/* ---------- home screen widget ---------- */

/** What the widget shows. Weeks are stored so the widget can roll over to a new week without the app running. */
data class WidgetInfo(
    /** Start of each week (Monday 00:00 local) that has a workout. */
    val weeks: List<Long>,
    /** (start, duration ms) of workouts in the last 8 days. */
    val recent: List<Pair<Long, Long>>,
    val goal: Int?,
    /** Start of the workout in progress, if any. */
    val activeStart: Long?,
    val nextRoutineId: String?,
    val nextRoutineName: String?,
)

data class WidgetNumbers(val workouts: Int, val minutes: Long, val streak: Int)

/** Workouts and minutes this week, and the current weekly streak, as of [now]. */
fun widgetNumbers(weeks: Collection<Long>, recent: List<Pair<Long, Long>>, now: Long): WidgetNumbers {
    val ws = weekStart(now)
    val mine = recent.filter { it.first >= ws }
    val set = weeks.toSet()
    var k = ws; if (k !in set) k = addDays(k, -7)
    var streak = 0; while (k in set) { streak++; k = addDays(k, -7) }
    return WidgetNumbers(mine.size, mine.sumOf { it.second } / 60_000, streak)
}

/** The routine to suggest next: the one after the last routine you did, in your list order. */
fun nextRoutine(idx: Index): Routine? {
    if (idx.rs.isEmpty()) return null
    val last = idx.ws.firstOrNull { it.routineId != null && idx.data.routines[it.routineId] != null }?.routineId ?: return idx.rs.first()
    val i = idx.rs.indexOfFirst { it.id == last }
    return idx.rs[(i + 1) % idx.rs.size]
}

fun widgetInfo(idx: Index, now: Long): WidgetInfo {
    val from = now - 8L * 86_400_000
    val r = nextRoutine(idx)
    return WidgetInfo(
        weeks = idx.ws.map { weekStart(it.start) }.distinct().sorted(),
        recent = idx.ws.filter { it.start >= from }.map { it.start to (it.end - it.start) },
        goal = idx.settings.goal, activeStart = idx.data.active?.takeIf { it.editId == null }?.start,
        nextRoutineId = r?.id, nextRoutineName = r?.name,
    )
}
