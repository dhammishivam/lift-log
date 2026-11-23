package app.liftlog.logic

import app.liftlog.model.Exercise
import app.liftlog.model.Workout

/** The body-map regions, as drawn in shared/body.json. */
val REGIONS = listOf("Chest", "Front delts", "Side delts", "Rear delts", "Biceps", "Triceps", "Forearms", "Abs", "Obliques",
    "Traps", "Upper back", "Lats", "Lower back", "Glutes", "Abductors", "Quads", "Hamstrings", "Adductors", "Calves")

/** Region → the six groups of the balance chart. */
val REGION_GROUP = mapOf(
    "Chest" to "Chest", "Front delts" to "Shoulders", "Side delts" to "Shoulders", "Rear delts" to "Shoulders",
    "Biceps" to "Arms", "Triceps" to "Arms", "Forearms" to "Arms", "Abs" to "Core", "Obliques" to "Core",
    "Traps" to "Back", "Upper back" to "Back", "Lats" to "Back", "Lower back" to "Back",
    "Glutes" to "Legs", "Abductors" to "Legs", "Quads" to "Legs", "Hamstrings" to "Legs", "Adductors" to "Legs", "Calves" to "Legs",
)

/** The big muscles worth 10+ sets a week; used for the "under 10 a week" hint. */
val MAJOR = listOf("Chest", "Lats", "Upper back", "Front delts", "Side delts", "Rear delts", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes", "Calves", "Abs")

/**
 * How much one working set of [x] counts for each region: 1 for the target, ½ for the muscles that help.
 * Worked out from the exercise's main muscle and its name (press, row, pulldown, deadlift…), so imported and custom
 * exercises get sensible credit too. Same rules as muscleTargets() on the web.
 */
fun muscleTargets(x: Exercise): Map<String, Double> {
    val n = x.name.lowercase()
    fun has(vararg k: String) = k.any { it in n }
    fun m(vararg p: Pair<String, Double>) = linkedMapOf(*p)
    return when (x.muscle) {
        "Chest" -> when {
            has("fly", "butterfly", "pec deck", "crossover") -> m("Chest" to 1.0, "Front delts" to 0.5)
            has("dip") -> m("Chest" to 1.0, "Triceps" to 0.5, "Front delts" to 0.5)
            has("incline") -> m("Chest" to 1.0, "Front delts" to 0.5, "Triceps" to 0.5)
            else -> m("Chest" to 1.0, "Triceps" to 0.5, "Front delts" to 0.5)
        }
        "Back" -> when {
            has("deadlift") -> m("Lower back" to 1.0, "Glutes" to 0.5, "Hamstrings" to 0.5, "Traps" to 0.5, "Forearms" to 0.5)
            has("extension", "hyperextension", "good morning", "superman") -> m("Lower back" to 1.0, "Glutes" to 0.5, "Hamstrings" to 0.5)
            has("straight arm", "pullover") -> m("Lats" to 1.0)
            has("pulldown", "pull up", "pull-up", "pullup", "chin") -> m("Lats" to 1.0, "Biceps" to 0.5, "Upper back" to 0.5)
            has("row") -> m("Upper back" to 1.0, "Lats" to 0.5, "Rear delts" to 0.5, "Biceps" to 0.5)
            else -> m("Lats" to 1.0, "Upper back" to 0.5)
        }
        "Traps" -> m("Traps" to 1.0, "Forearms" to 0.5)
        "Shoulders" -> when {
            has("lateral", "side raise", "y raise") -> m("Side delts" to 1.0)
            has("upright") -> m("Side delts" to 1.0, "Traps" to 0.5)
            has("rear", "reverse fly", "face pull") -> m("Rear delts" to 1.0, "Upper back" to 0.5)
            has("front raise") -> m("Front delts" to 1.0)
            has("press", "push up", "pike") -> m("Front delts" to 1.0, "Side delts" to 0.5, "Triceps" to 0.5)
            else -> m("Front delts" to 1.0, "Side delts" to 0.5)
        }
        "Biceps" -> m("Biceps" to 1.0, "Forearms" to 0.5)
        "Triceps" -> when {
            has("close grip", "dip") -> m("Triceps" to 1.0, "Chest" to 0.5, "Front delts" to 0.5)
            else -> m("Triceps" to 1.0)
        }
        "Forearms" -> m("Forearms" to 1.0)
        "Quads" -> when {
            has("extension") -> m("Quads" to 1.0)
            has("lunge", "split", "step up", "step-up") -> m("Quads" to 1.0, "Glutes" to 0.5, "Adductors" to 0.5, "Hamstrings" to 0.5)
            else -> m("Quads" to 1.0, "Glutes" to 0.5, "Adductors" to 0.5)
        }
        "Hamstrings" -> when {
            has("curl") -> m("Hamstrings" to 1.0)
            else -> m("Hamstrings" to 1.0, "Glutes" to 0.5, "Lower back" to 0.5)
        }
        "Glutes" -> when {
            has("abduct") -> m("Abductors" to 1.0, "Glutes" to 0.5)
            else -> m("Glutes" to 1.0, "Hamstrings" to 0.5)
        }
        "Adductors" -> m("Adductors" to 1.0)
        "Calves" -> m("Calves" to 1.0)
        "Abs" -> when {
            has("oblique", "twist", "side", "woodchop", "pallof") -> m("Obliques" to 1.0, "Abs" to 0.5)
            has("plank", "climber", "wheel", "rollout") -> m("Abs" to 1.0, "Obliques" to 0.5)
            else -> m("Abs" to 1.0)
        }
        "Full body" -> when {
            has("swing") -> m("Glutes" to 1.0, "Hamstrings" to 0.5, "Lower back" to 0.5)
            has("burpee", "thruster") -> m("Quads" to 0.5, "Chest" to 0.5, "Front delts" to 0.5)
            else -> m()
        }
        else -> m()
    }
}

/** Credited working sets per region for these workouts (warm-ups left out). */
fun regionSets(workouts: List<Workout>, exOf: (String) -> Exercise): Map<String, Double> {
    val out = HashMap<String, Double>()
    workouts.forEach { w -> w.exercises.forEach { it2 ->
        val n = working(it2.sets).size; if (n == 0) return@forEach
        muscleTargets(exOf(it2.eid)).forEach { (r, k) -> out[r] = (out[r] ?: 0.0) + n * k }
    } }
    return out
}

/** Sum of regions per balance group. */
fun groupSets(regions: Map<String, Double>): Map<String, Double> =
    HashMap<String, Double>().also { g -> regions.forEach { (r, v) -> REGION_GROUP[r]?.let { k -> g[k] = (g[k] ?: 0.0) + v } } }

/** Shade for sets a week: 0 none, 1 under 4, 2 under 10, 3 for 10–20 (the productive range), 4 above 20. */
fun weeklyLevel(perWeek: Double): Int = when {
    perWeek <= 0.0 -> 0
    perWeek < 4 -> 1
    perWeek < 10 -> 2
    perWeek <= 20 -> 3
    else -> 4
}

/** Shade for one workout's sets on a region. */
fun sessionLevel(sets: Double): Int = when {
    sets <= 0.0 -> 0
    sets < 2 -> 1
    sets < 4 -> 2
    sets <= 8 -> 3
    else -> 4
}

/** Recovery state from hours since a region last got 2+ sets: 3 under 24 h, 2 under 48 h, 1 under 72 h, 0 fresh. */
fun recoveryLevel(hoursAgo: Double?): Int = when {
    hoursAgo == null -> 0
    hoursAgo < 24 -> 3
    hoursAgo < 48 -> 2
    hoursAgo < 72 -> 1
    else -> 0
}

/** When each region last got at least [minSets] credited sets in one workout (workouts newest first). */
fun lastTrained(workouts: List<Workout>, exOf: (String) -> Exercise, minSets: Double = 2.0): Map<String, Long> {
    val out = HashMap<String, Long>()
    workouts.forEach { w ->
        regionSets(listOf(w), exOf).forEach { (r, v) -> if (v >= minSets && r !in out) out[r] = w.end.takeIf { it > 0 } ?: w.start }
    }
    return out
}

/** The exercises that gave a region its sets, most first: (exercise id, credited sets). */
fun regionExercises(workouts: List<Workout>, exOf: (String) -> Exercise, region: String): List<Pair<String, Double>> {
    val m = HashMap<String, Double>()
    workouts.forEach { w -> w.exercises.forEach { it2 ->
        val k = muscleTargets(exOf(it2.eid))[region] ?: return@forEach
        val n = working(it2.sets).size; if (n > 0) m[it2.eid] = (m[it2.eid] ?: 0.0) + n * k
    } }
    return m.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

/** Credited sets per region for the exercises of one workout; [doneOnly] counts only ticked sets (live workout). */
fun regionSetsOf(items: List<app.liftlog.model.ExItem>, exOf: (String) -> Exercise, doneOnly: Boolean = false): Map<String, Double> {
    val out = HashMap<String, Double>()
    items.forEach { it2 ->
        val n = working(it2.sets).count { s -> !doneOnly || s.done == true }; if (n == 0) return@forEach
        muscleTargets(exOf(it2.eid)).forEach { (r, k) -> out[r] = (out[r] ?: 0.0) + n * k }
    }
    return out.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key }).associate { it.key to it.value }
}

/** 12.5 → "12.5", 12.0 → "12". */
fun setsLabel(v: Double): String = fmtN(kotlin.math.round(v * 2) / 2)
