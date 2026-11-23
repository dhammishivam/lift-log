package app.liftlog.logic

import app.liftlog.model.*
import kotlin.math.abs

/** One exercise of a program day: [reps] is seconds for timed exercises. */
data class ProgEx(val name: String, val sets: Int, val reps: Int, val rest: Int)
data class ProgDay(val name: String, val exercises: List<ProgEx>)
data class Program(
    val id: String, val name: String, val days: Int,
    /** beginner, intermediate, advanced or any */
    val level: String,
    /** gym, dumbbell or none */
    val equip: String,
    /** muscle, strength or fit */
    val goal: String,
    val about: String, val plan: List<ProgDay>,
)

/** The built-in programs (shared/programs.txt, same list as the web app). */
val PROGRAMS: List<Program> by lazy { parsePrograms(PROGRAMS_TXT) }

fun parsePrograms(text: String): List<Program> {
    val out = mutableListOf<Program>()
    var head: List<String>? = null; val days = mutableListOf<ProgDay>(); var day: String? = null; val exs = mutableListOf<ProgEx>()
    fun closeDay() { day?.let { days += ProgDay(it, exs.toList()) }; day = null; exs.clear() }
    fun closeProg() { closeDay(); head?.let { h -> out += Program(h[0], h[1], h[2].toInt(), h[3], h[4], h[5], h[6], days.toList()) }; days.clear() }
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { l ->
        when {
            l.startsWith("#") -> { closeProg(); head = l.drop(1).split('|', limit = 7) }
            l.startsWith("@") -> { closeDay(); day = l.drop(1).trim() }
            else -> { val f = l.split('|'); exs += ProgEx(f[0], f[1].toInt(), f[2].toInt(), f[3].toInt()) }
        }
    }
    closeProg()
    return out
}

val LEVELS = listOf("new" to "New to lifting", "some" to "6 months to 2 years", "exp" to "2+ years")
val EQUIPS = listOf("gym" to "Full gym", "dumbbell" to "Dumbbells only", "none" to "No equipment")
val GOALS = listOf("muscle" to "Build muscle", "strength" to "Get stronger", "fit" to "Get fit")
val GENDERS = listOf("male" to "Male", "female" to "Female", "other" to "Other")
fun levelLabel(l: String) = when (l) { "beginner" -> "Beginner"; "intermediate" -> "Intermediate"; "advanced" -> "Advanced"; else -> "All levels" }
fun equipLabel(e: String) = EQUIPS.firstOrNull { it.first == e }?.second ?: e

/** A ranked suggestion; [note] explains a mismatch, e.g. a different number of days. */
data class Suggestion(val program: Program, val note: String?)

/** Programs you can do with your equipment, best match first: right number of days, then level, then goal. */
fun recommend(p: Profile, days: Int): List<Suggestion> {
    val canUse = when (p.equip) { "none" -> setOf("none"); "dumbbell" -> setOf("dumbbell", "none"); else -> setOf("gym", "dumbbell", "none") }
    fun levelScore(l: String) = when (p.exp) {
        "new" -> when (l) { "beginner" -> 4; "any" -> 3; "intermediate" -> 0; else -> -6 }
        "exp" -> when (l) { "advanced", "intermediate" -> 4; "any" -> 2; else -> -1 }
        else -> when (l) { "intermediate" -> 4; "any" -> 3; "beginner" -> 2; else -> -2 }
    }
    fun score(g: Program) = -abs(g.days - days) * 10 + levelScore(g.level) + (if (g.goal == p.goal) 2 else 0) + (if (g.equip == (p.equip ?: "gym")) 3 else 0)
    return PROGRAMS.filter { it.equip in canUse }.sortedByDescending { score(it) }.map { g ->
        Suggestion(g, if (g.days != days) "${g.days} days a week, the closest to your $days" else null)
    }
}

/**
 * A program's days as routines in a folder named after it. New lifters get at most 3 sets per exercise.
 * Timed exercises get their seconds as the target; everything else gets target reps.
 */
fun programRoutines(g: Program, p: Profile, idx: Index, now: Long): List<Routine> = g.plan.mapIndexed { i, d ->
    Routine(id = "r_" + rid(), name = d.name, folder = g.name, created = now + i, updated = now,
        exercises = d.exercises.map { e ->
            val eid = "b:" + slug(e.name); val t = idx.exOf(eid).type
            val sets = if (p.exp == "new") minOf(e.sets, 3) else e.sets
            ExItem(eid, "", e.rest, null, List(sets) { if (t == "dur") WSet(sec = e.reps.toDouble()) else WSet(reps = e.reps.toDouble()) })
        })
}
