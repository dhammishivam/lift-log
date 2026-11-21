package app.liftlog.logic

import app.liftlog.model.WSet
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.random.Random

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
fun rid(): String = nowMs().toString(36) + Random.nextLong(0, 36L * 36 * 36 * 36 * 36).toString(36)

fun r2(d: Double): Double = (d * 100).roundToLong() / 100.0
fun r1(d: Double): Double = (d * 10).roundToLong() / 10.0

/** Number with grouping and up to 2 decimals, trailing zeros dropped: 12,450 · 62.5 · 1.25 */
fun fmtN(d: Double?): String {
    if (d == null || d.isNaN()) return ""
    val cents = (abs(d) * 100).roundToLong()
    val whole = cents / 100
    val frac = (cents % 100).toInt()
    val w = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    val f = when {
        frac == 0 -> ""
        frac % 10 == 0 -> "." + (frac / 10)
        else -> "." + frac.toString().padStart(2, '0')
    }
    return (if (d < 0 && cents != 0L) "-" else "") + w + f
}

/** Plain number for an input field (no grouping). */
fun plainN(d: Double?): String {
    if (d == null) return ""
    val v = r2(d)
    return if (v == floor(v)) v.toLong().toString() else v.toString()
}

fun parseNum(s: String?): Double? {
    if (s == null) return null
    val t = s.trim().replace(',', '.')
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
}

fun fmtSec(sec: Double?): String {
    val s = (sec ?: 0.0).roundToLong()
    val h = s / 3600; val m = (s % 3600) / 60; val x = s % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${x.toString().padStart(2, '0')}" else "$m:${x.toString().padStart(2, '0')}"
}
fun clock(ms: Long): String = fmtSec(maxOf(0L, ms) / 1000.0)
fun dur(ms: Long): String {
    val m = maxOf(0L, (ms / 60000.0).roundToLong()); val h = m / 60
    return if (h > 0) "${h}h ${m % 60}m" else "${m}m"
}
fun fmtRest(s: Int?): String = when {
    s == null || s == 0 -> "Off"
    s < 60 -> "${s}s"
    s % 60 == 0 -> "${s / 60}min"
    else -> "${s / 60}min ${s % 60}s"
}

val tz: TimeZone get() = TimeZone.currentSystemDefault()
fun ldt(ms: Long): LocalDateTime = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
fun startOfDay(d: LocalDate): Long = d.atStartOfDayIn(tz).toEpochMilliseconds()
fun msOf(dt: LocalDateTime): Long = dt.toInstant(tz).toEpochMilliseconds()

val MON3 = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
val DOW3 = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun yr(ms: Long) = ldt(ms).year
fun dShort(ms: Long): String {
    val d = ldt(ms); val y = if (d.year != yr(nowMs())) " ${d.year}" else ""
    return "${d.dayOfMonth} ${MON3[d.monthNumber - 1]}$y"
}
fun dLong(ms: Long): String {
    val d = ldt(ms)
    return "${DOW3[d.dayOfWeek.isoDayNumber - 1]}, ${dShort(ms)}"
}
fun tShort(ms: Long): String {
    val d = ldt(ms); val h = d.hour % 12
    return "${if (h == 0) 12 else h}:${d.minute.toString().padStart(2, '0')} ${if (d.hour < 12) "am" else "pm"}"
}
fun monthLabel(year: Int, month: Int) = "${MONTHS[month - 1]} $year"

fun weekStart(ms: Long): Long {
    val d = ldt(ms).date
    return startOfDay(d.minus(d.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY))
}
fun addDays(ms: Long, n: Int): Long = startOfDay(ldt(ms).date.plus(n, DateTimeUnit.DAY))
fun monthStart(year: Int, month: Int): Long = startOfDay(LocalDate(year, month, 1))
fun addMonths(year: Int, month: Int, n: Int): Pair<Int, Int> {
    val idx = year * 12 + (month - 1) + n
    return Pair(idx / 12, idx % 12 + 1)
}

/** Hevy's export date format: "21 Nov 2026, 19:41". */
fun hevyDate(ms: Long): String {
    val d = ldt(ms)
    return "${d.dayOfMonth} ${MON3[d.monthNumber - 1]} ${d.year}, ${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
}

private val MONI = MON3.mapIndexed { i, m -> m.lowercase() to i + 1 }.toMap()
/** Parses Hevy dates ("4 Jan 2024, 07:12", "4 Sept 2024, 07:12") and ISO strings. */
fun parseDate(s: String): Long? {
    val t = s.trim()
    val m = Regex("""^(\d{1,2})\s+([A-Za-z]{3})[a-z]*\.?\s+(\d{4}),?\s+(\d{1,2}):(\d{2})""").find(t)
    if (m != null) {
        val mon = MONI[m.groupValues[2].lowercase()] ?: return null
        return runCatching { msOf(LocalDateTime(m.groupValues[3].toInt(), mon, m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())) }.getOrNull()
    }
    return runCatching { Instant.parse(t).toEpochMilliseconds() }.getOrNull()
        ?: runCatching { msOf(LocalDateTime.parse(t.replace(' ', 'T'))) }.getOrNull()
        ?: runCatching { startOfDay(LocalDate.parse(t.take(10))) }.getOrNull()
}

/* ---------- units ---------- */
const val LB = 2.20462262
fun toU(kg: Double, lb: Boolean) = if (lb) kg * LB else kg
fun fromU(v: Double, lb: Boolean) = if (lb) v / LB else v
fun unitOf(lb: Boolean) = if (lb) "lb" else "kg"
fun wt(kg: Double?, lb: Boolean) = "${fmtN(toU(kg ?: 0.0, lb))} ${unitOf(lb)}"
fun wv(kg: Double, lb: Boolean) = "${fmtN(toU(kg, lb).roundToLong().toDouble())} ${unitOf(lb)}"
fun hdr(type: String, f: String, h: String, lb: Boolean) = if (f == "kg") (if (type == "wbw") "+" else "") + unitOf(lb) else h

/** Value of one field as shown in an input. */
fun fieldVal(s: WSet?, f: String, lb: Boolean): Double? = when {
    s == null -> null
    f == "min" -> s.sec?.let { r2(it / 60) }
    f == "kg" -> s.kg?.let { r2(toU(it, lb)) }
    f == "reps" -> s.reps
    f == "km" -> s.km
    f == "sec" -> s.sec
    else -> null
}
fun withField(s: WSet, f: String, v: Double?, lb: Boolean): WSet = when (f) {
    "min" -> s.copy(sec = v?.let { (it * 60).roundToLong().toDouble() })
    "kg" -> s.copy(kg = v?.let { (fromU(it, lb) * 1000).roundToLong() / 1000.0 })
    "reps" -> s.copy(reps = v)
    "km" -> s.copy(km = v)
    "sec" -> s.copy(sec = v)
    else -> s
}
fun valsAsSet(v: app.liftlog.model.SetVals?): WSet? = v?.let { WSet(kg = it.kg, reps = it.reps, km = it.km, sec = it.sec) }

fun fmtSet(type: String, s: WSet?, lb: Boolean): String {
    if (s == null) return ""
    val kg = fmtN(toU(s.kg ?: 0.0, lb)); val u = unitOf(lb); val r = fmtN(s.reps ?: 0.0)
    val q = s.rpe?.let { " @${fmtN(it)}" } ?: ""
    return when (type) {
        "wbw" -> "+$kg $u × $r$q"
        "bw" -> "$r reps$q"
        "dur" -> fmtSec(s.sec)
        "cd" -> "${fmtN(s.km ?: 0.0)} km · ${fmtSec(s.sec)}"
        else -> "$kg $u × $r$q"
    }
}
fun setLabel(sets: List<WSet>, i: Int): String {
    val s = sets[i]
    return if (s.t == "n") sets.take(i + 1).count { it.t == "n" }.toString() else s.t.uppercase()
}
