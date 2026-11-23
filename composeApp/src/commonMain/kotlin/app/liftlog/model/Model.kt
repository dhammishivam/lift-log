package app.liftlog.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Documents use exactly the same JSON shape as the Lift Log web app, so a backup
 * from one restores in the other (and a future cloud sync can move them as-is).
 */
val JsonX = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
}

@Serializable
data class SetVals(
    val kg: Double? = null,
    val reps: Double? = null,
    val km: Double? = null,
    val sec: Double? = null,
)

/** One set. `t` is n(ormal), w(arm-up), d(rop) or f(ailure). `done`/`p` only exist while logging. */
@Serializable
data class WSet(
    val t: String = "n",
    val kg: Double? = null,
    val reps: Double? = null,
    val km: Double? = null,
    val sec: Double? = null,
    val rpe: Double? = null,
    val done: Boolean? = null,
    val p: SetVals? = null,
)

@Serializable
data class ExItem(
    val eid: String,
    val note: String? = null,
    val rest: Int? = null,
    val ss: Int? = null,
    val sets: List<WSet> = emptyList(),
)

@Serializable
data class Workout(
    val kind: String = "workout",
    val id: String,
    val title: String = "Workout",
    val notes: String? = null,
    val start: Long,
    val end: Long,
    val routineId: String? = null,
    val exercises: List<ExItem> = emptyList(),
    val src: String? = null,
)

/** The workout being logged (or an existing workout being edited when `editId` is set). */
@Serializable
data class Active(
    val kind: String = "active",
    val id: String,
    val editId: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val start: Long,
    val end: Long? = null,
    val routineId: String? = null,
    val exercises: List<ExItem> = emptyList(),
)

@Serializable
data class Routine(
    val kind: String = "routine",
    val id: String,
    val name: String,
    val folder: String? = null,
    val created: Long? = null,
    val updated: Long? = null,
    val exercises: List<ExItem> = emptyList(),
)

@Serializable
data class CustomEx(
    val kind: String = "exercise",
    val id: String,
    val name: String,
    val muscle: String = "Other",
    val equip: String = "Other",
    val type: String = "wr",
    val cue: String? = null,
)

@Serializable
data class Measure(
    val kind: String = "measure",
    val id: String,
    val date: Long,
    val v: Map<String, Double> = emptyMap(),
    val photos: List<String>? = null,
    val src: String? = null,
)

@Serializable
data class Settings(
    val kind: String = "settings",
    val rest: Int = 90,
    val unit: String? = null,
    val sug: Boolean? = null,
    val lo: Int? = null,
    val hi: Int? = null,
    val awake: Boolean? = null,
    val bwv: Boolean? = null,
    val goal: Int? = null,
    /** "dark" (default when null), "light" or "system". */
    val theme: String? = null,
    /** What the program finder asked: used to suggest programs. Stays on this phone. */
    val profile: Profile? = null,
    /** Mascot celebrations for records (on unless false). */
    val cele: Boolean? = null,
    /** Shown on the Profile tab. */
    val name: String? = null,
    /** Steps of the Move from Hevy guide the user has ticked off. */
    val hevy: List<String>? = null,
    /** App only: Anthropic API key and model for the coach. Never exported. */
    val apiKey: String? = null,
    val model: String? = null,
)

/** Answers from "Find a program". Bodyweight goes into measurements instead. */
@Serializable
data class Profile(
    /** male, female or other */
    val gender: String? = null,
    val heightCm: Double? = null,
    /** new, some or exp */
    val exp: String? = null,
    /** gym, dumbbell or none */
    val equip: String? = null,
    /** muscle, strength or fit */
    val goal: String? = null,
    val days: Int? = null,
)

@Serializable
data class Pins(
    val kind: String = "pins",
    val n: Map<String, String> = emptyMap(),
)

/** Library or custom exercise, unified. */
data class Exercise(
    val id: String,
    val name: String,
    val muscle: String,
    val equip: String,
    val type: String,
    val custom: Boolean = false,
    val cue: String? = null,
)

/** Everything the app stores, held in memory and mirrored to SQLite. */
data class Data(
    val workouts: Map<String, Workout> = emptyMap(),
    val routines: Map<String, Routine> = emptyMap(),
    val customs: Map<String, CustomEx> = emptyMap(),
    val measures: Map<String, Measure> = emptyMap(),
    val settings: Settings = Settings(),
    val pins: Pins = Pins(),
    val active: Active? = null,
)
