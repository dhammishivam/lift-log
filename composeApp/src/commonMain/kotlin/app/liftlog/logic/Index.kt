package app.liftlog.logic

import app.liftlog.model.*
import kotlin.math.max
import kotlin.math.roundToInt

/** Per-set metrics used for records and charts. */
data class SetMetrics(val w: Double, val orm: Double, val vol: Double, val reps: Double, val sec: Double, val km: Double) {
    operator fun get(k: String): Double = when (k) { "w" -> w; "orm" -> orm; "vol" -> vol; "reps" -> reps; "sec" -> sec; "km" -> km; else -> 0.0 }
}
fun setM(s: WSet): SetMetrics {
    val kg = s.kg ?: 0.0; val r = s.reps ?: 0.0
    // Epley; past 12 reps the estimate runs away (20 kg × 30 would claim a 40 kg max), so higher sets count as 12.
    val orm = if (r > 0) (if (r == 1.0) kg else kg * (1 + minOf(r, 12.0) / 30)) else 0.0
    return SetMetrics(kg, orm, kg * r, r, s.sec ?: 0.0, s.km ?: 0.0)
}
fun isVol(t: String) = t == "wr" || t == "wbw"
fun working(sets: List<WSet>) = sets.filter { it.t != "w" }

data class HEntry(val wid: String, val start: Long, val sets: List<WSet>, val rest: Int?, val w: Workout)
data class PrMarks(val n: Int, val m: Map<String, List<String>>)

/** Derived, read-only view of the data: sorted lists, exercise map, per-exercise history and record marks. */
class Index(val data: Data) {
    val ws: List<Workout> = data.workouts.values.sortedByDescending { it.start }
    val rs: List<Routine> = data.routines.values.sortedBy { it.created ?: 0L }
    val ms: List<Measure> = data.measures.values.sortedByDescending { it.date }
    val ex: Map<String, Exercise>
    val h: Map<String, List<HEntry>>
    val prm: Map<String, PrMarks>
    val settings get() = data.settings
    val lb get() = data.settings.unit == "lb"

    init {
        val m = LinkedHashMap<String, Exercise>()
        LIBRARY.forEach { m[it.id] = it }
        // A custom exercise with a library name (often one brought over from Hevy) replaces that library entry, so the
        // list shows it once; it borrows the library's muscle, equipment and cue when it has none of its own.
        val byName = LIBRARY.associateBy { it.name.lowercase() }
        data.customs.values.forEach { c ->
            val lib = byName[c.name.lowercase()]
            if (lib != null) m.remove(lib.id)
            m[c.id] = Exercise(c.id, c.name, if (c.muscle == "Other" && lib != null) lib.muscle else c.muscle,
                if (c.equip == "Other" && lib != null) lib.equip else c.equip, c.type, true, c.cue ?: lib?.cue)
        }
        ex = m
        val hh = HashMap<String, MutableList<HEntry>>()
        val pr = HashMap<String, PrMarks>()
        val run = HashMap<String, Map<String, Double>>()
        ws.asReversed().forEach { w ->
            val marks = HashMap<String, MutableList<String>>()
            w.exercises.forEachIndexed { ei, it ->
                val t = exOf(it.eid).type; val keys = PRKEYS[t] ?: emptyList(); val best = run[it.eid]
                hh.getOrPut(it.eid) { mutableListOf() }.add(HEntry(w.id, w.start, it.sets, it.rest, w))
                val mx = keys.associateWith { 0.0 }.toMutableMap(); val at = keys.associateWith { -1 }.toMutableMap()
                it.sets.forEachIndexed { si, s -> if (s.t != "w") { val sm = setM(s); keys.forEach { k -> if (sm[k] > mx[k]!!) { mx[k] = sm[k]; at[k] = si } } } }
                if (best != null) keys.forEach { k -> if (at[k]!! >= 0 && mx[k]!! > (best[k] ?: 0.0) + 1e-9) marks.getOrPut("$ei:${at[k]}") { mutableListOf() }.add(k) }
                run[it.eid] = if (best != null) keys.associateWith { k -> max(best[k] ?: 0.0, mx[k]!!) } else mx
            }
            pr[w.id] = PrMarks(marks.values.sumOf { it.size }, marks) // n counts records (a set can set several)
        }
        hh.values.forEach { it.reverse() }
        h = hh; prm = pr
    }

    fun exOf(id: String): Exercise = ex[id] ?: Exercise(id, "Unknown exercise", "Other", "Other", "wr")
    fun cue(x: Exercise): String? = x.cue?.takeIf { it.isNotBlank() } ?: CUES[x.name]
    fun pin(eid: String): String = data.pins.n[eid] ?: ""

    fun prevSets(eid: String, start: Long, excl: String?): List<WSet>? = h[eid]?.firstOrNull { it.start < start && it.wid != excl }?.sets

    fun lastRest(eid: String): Int = h[eid]?.firstOrNull { it.rest != null && !(it.w.src == "hevy" && it.rest == 0) }?.rest ?: settings.rest

    /** Records set in a live workout (best set per metric that beats all earlier sessions). */
    fun livePrs(a: Active, ei: Int): Map<Int, List<String>> {
        val it = a.exercises.getOrNull(ei) ?: return emptyMap()
        val t = exOf(it.eid).type; val keys = PRKEYS[t] ?: return emptyMap()
        val hist = h[it.eid]?.filter { e -> e.start < a.start && e.wid != a.editId } ?: return emptyMap()
        if (hist.isEmpty()) return emptyMap()
        val b = keys.associateWith { k -> hist.maxOf { e -> working(e.sets).maxOfOrNull { setM(it)[k] } ?: 0.0 } }
        val out = HashMap<Int, MutableList<String>>()
        keys.forEach { k ->
            var mx = b[k]!!; var at = -1
            it.sets.forEachIndexed { si, s -> if (s.done == true && s.t != "w") { val v = setM(s)[k]; if (v > mx + 1e-9) { mx = v; at = si } } }
            if (at >= 0) out.getOrPut(at) { mutableListOf() }.add(k)
        }
        return out
    }

    fun bwAt(ts: Long): Double? {
        var b: Double? = null
        for (m in ms) { val v = m.v["weight_kg"] ?: continue; if (m.date <= ts) return v; b = v }
        return b
    }
    fun setVol(t: String, s: WSet, ts: Long): Double {
        val kg = s.kg ?: 0.0; val r = s.reps ?: 0.0
        if (t == "wr") return kg * r
        if (settings.bwv != true) return if (t == "wbw") kg * r else 0.0
        if (t == "wbw" || t == "bw") return ((bwAt(ts) ?: 0.0) + (if (t == "wbw") kg else 0.0)) * r
        return 0.0
    }
    fun wVol(w: Workout): Double = w.exercises.sumOf { it2 -> val t = exOf(it2.eid).type; it2.sets.sumOf { setVol(t, it, w.start) } }
    fun wSets(w: Workout): Int = w.exercises.sumOf { it.sets.size }
    fun liveStats(a: Active): Pair<Double, Int> {
        var vol = 0.0; var n = 0
        a.exercises.forEach { it2 -> val t = exOf(it2.eid).type; it2.sets.forEach { if (it.done == true) { n++; vol += setVol(t, it, a.start) } } }
        return vol to n
    }

    /** Next-weight suggestion for a set, from last session (double progression). */
    fun sugFor(a: Active, ei: Int, si: Int): Sug? {
        if (settings.sug == false) return null
        val it = a.exercises.getOrNull(ei) ?: return null
        val t = exOf(it.eid).type; val s = it.sets.getOrNull(si) ?: return null
        if (s.t != "n" || t !in listOf("wr", "wbw", "bw")) return null
        val prev = prevSets(it.eid, a.start, a.editId) ?: return null
        val k = it.sets.take(si + 1).count { it.t == "n" } - 1
        val last = prev.filter { it.t == "n" }.getOrNull(k) ?: return null
        val lr = last.reps ?: return null
        if (lr <= 0) return null
        val target = s.p?.reps; val lo = settings.lo ?: 8; val hi = settings.hi ?: 12
        val easy = last.rpe == null || last.rpe <= 8.5
        if (t == "bw") return Sug(null, if (easy && (target == null || lr >= target)) lr + 1 else (target ?: lr), false)
        val kg = last.kg ?: 0.0
        val upK = fromU(r2(toU(kg, lb) + (if (lb) 5.0 else 2.5)), lb)
        if (target != null) return if (lr >= target && easy) Sug(upK, target, true) else Sug(kg, target, false)
        if (lr >= hi && easy) return Sug(upK, if (lr > hi + 2) lr - 2 else lo.toDouble(), true)
        return Sug(kg, lr + if (easy) 1 else 0, false)
    }

    fun stalled(eid: String): Boolean {
        if (!isVol(exOf(eid).type)) return false
        val hist = h[eid] ?: return false
        val now = nowMs(); val d28 = now - 28L * 86_400_000; val d84 = now - 84L * 86_400_000
        val a = hist.filter { it.start >= d28 }; val b = hist.filter { it.start in d84 until d28 }
        if (a.size < 3 || b.size < 2) return false
        fun best(l: List<HEntry>) = l.maxOf { e -> working(e.sets).maxOfOrNull { setM(it).orm } ?: 0.0 }
        return best(a) <= best(b) + 1e-9
    }

    fun weekRuns(): Pair<Int, Int> {
        val weeks = ws.map { weekStart(it.start) }.toSortedSet()
        var k = weekStart(nowMs()); if (k !in weeks) k = addDays(k, -7)
        var cur = 0; while (k in weeks) { cur++; k = addDays(k, -7) }
        var best = 0; var run = 0; var prev: Long? = null
        weeks.forEach { x -> run = if (prev != null && ((x - prev!!) / 604_800_000.0).roundToInt() == 1) run + 1 else 1; best = max(best, run); prev = x }
        return cur to best
    }

    /** Routine per workout name used 3+ times in the last 120 days, built from its latest session. */
    fun routinesFromHistory(): List<Routine> {
        val by = LinkedHashMap<String, Pair<Int, Workout>>()
        ws.forEach { w -> val k = w.title.trim(); if (k.isNotEmpty()) { val g = by[k]; by[k] = if (g == null) 1 to w else (g.first + 1) to g.second } }
        val names = rs.map { it.name.lowercase() }.toSet(); val from = nowMs() - 120L * 86_400_000; val base = nowMs()
        return by.entries.filter { (k, g) -> g.first >= 3 && g.second.start >= from && k.lowercase() !in names }
            .mapIndexed { i, (k, g) -> Routine(id = "r_" + rid(), name = k, created = base + i, updated = base,
                exercises = g.second.exercises.map { it2 -> ExItem(it2.eid, "", it2.rest?.takeIf { r -> r > 0 } ?: lastRest(it2.eid), it2.ss, it2.sets.map { s -> WSet(t = s.t, kg = s.kg, reps = s.reps, km = s.km, sec = s.sec) }) }) }
    }
}

data class Sug(val kg: Double?, val reps: Double?, val up: Boolean) {
    fun asSet() = WSet(kg = kg, reps = reps)
}

/* ---------- supersets ---------- */
val SS_COLORS = listOf(0xFF3D84FF, 0xFFE8930A, 0xFF9B7BF5, 0xFF16A34A, 0xFFEF4444)
fun ssInfo(items: List<ExItem>): (Int?) -> Pair<Char, Long>? {
    val o = items.mapNotNull { it.ss }.distinct()
    return { ss -> val i = if (ss == null) -1 else o.indexOf(ss); if (i < 0) null else ('A' + i % 26) to SS_COLORS[i % SS_COLORS.size] }
}
fun normSS(items: List<ExItem>): List<ExItem> {
    val c = items.mapNotNull { it.ss }.groupingBy { it }.eachCount()
    return items.mapIndexed { i, e ->
        val ok = e.ss != null && (c[e.ss] ?: 0) >= 2 && (items.getOrNull(i + 1)?.ss == e.ss || items.getOrNull(i - 1)?.ss == e.ss)
        if (e.ss != null && !ok) e.copy(ss = null) else e
    }
}

/* ---------- volume comparison ---------- */
private val OBJ = listOf(Triple(15.0, "a bicycle", "bicycles"), Triple(70.0, "a washing machine", "washing machines"), Triple(180.0, "a motorbike", "motorbikes"),
    Triple(450.0, "a grand piano", "grand pianos"), Triple(1300.0, "a car", "cars"), Triple(2300.0, "a rhino", "rhinos"),
    Triple(6000.0, "an elephant", "elephants"), Triple(12000.0, "a city bus", "city buses"), Triple(40000.0, "a loaded truck", "loaded trucks"))
fun compareWeight(kg: Double): String {
    val o = OBJ.lastOrNull { kg >= it.first } ?: return ""
    val n = kg / o.first
    return if (n < 1.25) "About the weight of ${o.second}" else "About the weight of ${fmtN(if (n < 10) (n * 2).roundToInt() / 2.0 else n.roundToInt().toDouble())} ${o.third}"
}

/* ---------- plates & 1RM ---------- */
val PLATES = mapOf("kg" to listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25), "lb" to listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5))
val BARS = mapOf("kg" to listOf(20.0, 15.0, 10.0, 0.0), "lb" to listOf(45.0, 35.0, 15.0, 0.0))
fun plates(total: Double, bar: Double, unit: String): Pair<List<Double>, Double> {
    var side = (total - bar) / 2; val out = mutableListOf<Double>()
    for (p in PLATES.getValue(unit)) while (side >= p - 1e-9) { out += p; side -= p }
    return out to r2(side * 2)
}
fun oneRm(w: Double, r: Double): Triple<Double, Double, Double> {
    val ep = if (r == 1.0) w else w * (1 + r / 30)
    val br = if (r == 1.0) w else w * 36 / (37 - r)
    return Triple((ep + br) / 2, ep, br)
}
