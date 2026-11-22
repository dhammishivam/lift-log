package app.liftlog

import app.liftlog.logic.msOf
import app.liftlog.model.*
import kotlinx.datetime.LocalDateTime

const val BENCH = "b:bench-press-barbell"
const val PULLUP = "b:pull-up"
const val PLANK = "b:plank"

/** Local time in the test machine's zone, the same way the app reads dates. */
fun at(y: Int, mo: Int, d: Int, h: Int = 18, mi: Int = 0): Long = msOf(LocalDateTime(y, mo, d, h, mi))

fun set(kg: Double?, reps: Double?, t: String = "n", rpe: Double? = null, done: Boolean? = null) = WSet(t = t, kg = kg, reps = reps, rpe = rpe, done = done)

fun workout(id: String, start: Long, vararg items: ExItem, title: String = "Push", src: String? = null) =
    Workout(id = id, title = title, start = start, end = start + 3_600_000, exercises = items.toList(), src = src)

fun item(eid: String, vararg sets: WSet, rest: Int? = null, ss: Int? = null) = ExItem(eid = eid, rest = rest, ss = ss, sets = sets.toList())

fun data(vararg ws: Workout, settings: Settings = Settings(), measures: List<Measure> = emptyList(), routines: List<Routine> = emptyList()) =
    Data(workouts = ws.associateBy { it.id }, settings = settings, measures = measures.associateBy { it.id }, routines = routines.associateBy { it.id })

/** A small Hevy workout export: two sessions, warm-up and drop sets, RPE, a superset and an exercise Lift Log doesn't know. */
val HEVY_WORKOUTS = """
"title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
"Push","4 Jan 2026, 07:12","4 Jan 2026, 08:20","Felt strong, ""easy"" day","Bench Press (Barbell)",,"Pause reps",0,"warmup",40,10,,,
"Push","4 Jan 2026, 07:12","4 Jan 2026, 08:20","Felt strong, ""easy"" day","Bench Press (Barbell)",,"Pause reps",1,"normal",80,8,,,8
"Push","4 Jan 2026, 07:12","4 Jan 2026, 08:20","Felt strong, ""easy"" day","Bench Press (Barbell)",,"Pause reps",2,"dropset",60,10,,,
"Push","4 Jan 2026, 07:12","4 Jan 2026, 08:20","Felt strong, ""easy"" day","Pull Up",0,,0,"normal",,12,,,
"Push","4 Jan 2026, 07:12","4 Jan 2026, 08:20","Felt strong, ""easy"" day","Cable Crunch Deluxe",0,,0,"failure",35,15,,,
"Legs","6 Sept 2026, 18:05","6 Sept 2026, 19:00","","Plank",,,0,"normal",,,,90,
""".trimIndent()

val HEVY_MEASURES = """
"date","weight_kg","fat_percent","neck_in","shoulder_in","chest_in","left_bicep_in","right_bicep_in","left_forearm_in","right_forearm_in","abdomen","waist","hips","left_thigh","right_thigh","left_calf","right_calf"
"1 Mar 2026, 08:00",78.4,18.5,,,,,,,,,,,,,,
"8 Mar 2026, 08:00",77.9,,,,,,,,,,,,,,,
"9 Mar 2026, 08:00",,,,,,,,,,,,,,,,
""".trimIndent()
