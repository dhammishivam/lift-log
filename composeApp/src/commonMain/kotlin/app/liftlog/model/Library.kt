package app.liftlog.model

/** Built-in exercises. Names match Hevy's so a Hevy export links to them. name|muscle|equipment|type */
private const val LIB_TXT = """Bench Press (Barbell)|Chest|Barbell|wr
Bench Press (Dumbbell)|Chest|Dumbbell|wr
Incline Bench Press (Barbell)|Chest|Barbell|wr
Incline Bench Press (Dumbbell)|Chest|Dumbbell|wr
Decline Bench Press (Barbell)|Chest|Barbell|wr
Chest Press (Machine)|Chest|Machine|wr
Chest Fly (Dumbbell)|Chest|Dumbbell|wr
Butterfly (Pec Deck)|Chest|Machine|wr
Cable Fly Crossovers|Chest|Cable|wr
Push Up|Chest|Bodyweight|bw
Chest Dip|Chest|Bodyweight|bw
Deadlift (Barbell)|Back|Barbell|wr
Pull Up|Back|Bodyweight|bw
Chin Up|Back|Bodyweight|bw
Pull Up (Weighted)|Back|Bodyweight|wbw
Lat Pulldown (Cable)|Back|Cable|wr
Bent Over Row (Barbell)|Back|Barbell|wr
Dumbbell Row|Back|Dumbbell|wr
Seated Cable Row - V Grip (Cable)|Back|Cable|wr
Seated Row (Machine)|Back|Machine|wr
T Bar Row|Back|Barbell|wr
Straight Arm Lat Pulldown (Cable)|Back|Cable|wr
Back Extension (Hyperextension)|Back|Bodyweight|bw
Shrug (Dumbbell)|Traps|Dumbbell|wr
Shrug (Barbell)|Traps|Barbell|wr
Overhead Press (Barbell)|Shoulders|Barbell|wr
Shoulder Press (Dumbbell)|Shoulders|Dumbbell|wr
Shoulder Press (Machine)|Shoulders|Machine|wr
Arnold Press (Dumbbell)|Shoulders|Dumbbell|wr
Lateral Raise (Dumbbell)|Shoulders|Dumbbell|wr
Lateral Raise (Cable)|Shoulders|Cable|wr
Front Raise (Dumbbell)|Shoulders|Dumbbell|wr
Face Pull|Shoulders|Cable|wr
Rear Delt Reverse Fly (Dumbbell)|Shoulders|Dumbbell|wr
Rear Delt Reverse Fly (Machine)|Shoulders|Machine|wr
Upright Row (Barbell)|Shoulders|Barbell|wr
Bicep Curl (Barbell)|Biceps|Barbell|wr
Bicep Curl (Dumbbell)|Biceps|Dumbbell|wr
Bicep Curl (Cable)|Biceps|Cable|wr
EZ Bar Biceps Curl|Biceps|Barbell|wr
Hammer Curl (Dumbbell)|Biceps|Dumbbell|wr
Preacher Curl (Barbell)|Biceps|Barbell|wr
Preacher Curl (Machine)|Biceps|Machine|wr
Seated Incline Curl (Dumbbell)|Biceps|Dumbbell|wr
Concentration Curl|Biceps|Dumbbell|wr
Triceps Pushdown|Triceps|Cable|wr
Triceps Rope Pushdown|Triceps|Cable|wr
Overhead Triceps Extension (Cable)|Triceps|Cable|wr
Triceps Extension (Dumbbell)|Triceps|Dumbbell|wr
Skullcrusher (Barbell)|Triceps|Barbell|wr
Close Grip Bench Press|Triceps|Barbell|wr
Triceps Dip|Triceps|Bodyweight|bw
Squat (Barbell)|Quads|Barbell|wr
Front Squat|Quads|Barbell|wr
Hack Squat (Machine)|Quads|Machine|wr
Leg Press (Machine)|Quads|Machine|wr
Leg Extension (Machine)|Quads|Machine|wr
Goblet Squat|Quads|Dumbbell|wr
Bulgarian Split Squat|Quads|Dumbbell|wr
Walking Lunge (Dumbbell)|Quads|Dumbbell|wr
Romanian Deadlift (Barbell)|Hamstrings|Barbell|wr
Romanian Deadlift (Dumbbell)|Hamstrings|Dumbbell|wr
Lying Leg Curl (Machine)|Hamstrings|Machine|wr
Seated Leg Curl (Machine)|Hamstrings|Machine|wr
Good Morning (Barbell)|Hamstrings|Barbell|wr
Hip Thrust (Barbell)|Glutes|Barbell|wr
Glute Bridge|Glutes|Bodyweight|bw
Hip Abduction (Machine)|Glutes|Machine|wr
Hip Adduction (Machine)|Adductors|Machine|wr
Standing Calf Raise (Machine)|Calves|Machine|wr
Seated Calf Raise|Calves|Machine|wr
Calf Press (Machine)|Calves|Machine|wr
Crunch|Abs|Bodyweight|bw
Cable Crunch|Abs|Cable|wr
Hanging Leg Raise|Abs|Bodyweight|bw
Ab Wheel|Abs|Other|bw
Russian Twist (Bodyweight)|Abs|Bodyweight|bw
Sit Up|Abs|Bodyweight|bw
Plank|Abs|Bodyweight|dur
Side Plank|Abs|Bodyweight|dur
Shoulder Press (Machine Plates)|Shoulders|Machine|wr
Standing Military Press (Barbell)|Shoulders|Barbell|wr
Rear Delt Reverse Fly (Cable)|Shoulders|Cable|wr
Reverse Fly Single Arm (Cable)|Shoulders|Cable|wr
Lat Pulldown - Close Grip (Cable)|Back|Cable|wr
Reverse Grip Lat Pulldown (Cable)|Back|Cable|wr
Single Arm Lat Pulldown|Back|Cable|wr
Lat Pulldown (Machine)|Back|Machine|wr
Chest Supported Incline Row (Dumbbell)|Back|Dumbbell|wr
Decline Bench Press (Dumbbell)|Chest|Dumbbell|wr
Preacher Curl (Dumbbell)|Biceps|Dumbbell|wr
Behind the Back Curl (Cable)|Biceps|Cable|wr
Seated Dip Machine|Triceps|Machine|wr
Single Arm Tricep Extension (Dumbbell)|Triceps|Dumbbell|wr
Single Arm Triceps Pushdown (Cable)|Triceps|Cable|wr
Lunge (Dumbbell)|Quads|Dumbbell|wr
Straight Leg Deadlift|Hamstrings|Barbell|wr
Lying Leg Raise|Abs|Bodyweight|bw
Jackknife Sit Up|Abs|Bodyweight|bw
Ab Scissors|Abs|Bodyweight|bw
Heel Taps|Abs|Bodyweight|bw
Stair Machine (Steps)|Cardio|Machine|dur
Kettlebell Swing|Full body|Kettlebell|wr
Running|Cardio|None|cd
Treadmill|Cardio|Machine|cd
Cycling|Cardio|Machine|cd
Rowing Machine|Cardio|Machine|cd
Elliptical Trainer|Cardio|Machine|cd
Walking|Cardio|None|cd
Stair Machine|Cardio|Machine|dur
Jump Rope|Cardio|Other|dur"""

private const val CUE_TXT = """Bench Press (Barbell)|Shoulder blades pinched and down, feet planted. Lower to mid-chest with elbows about 45° from your sides, then press up and slightly back.
Bench Press (Dumbbell)|Lower the dumbbells to chest level with elbows about 45° from your sides, then press up until your arms are straight.
Incline Bench Press (Barbell)|Bench at 30–45°. Lower to the upper chest, keep your shoulder blades back, press straight up.
Incline Bench Press (Dumbbell)|Bench at 30–45°. Lower to upper-chest level, press up and slightly together.
Decline Bench Press (Barbell)|Lock your legs in. Lower to the lower chest under control and press up.
Decline Bench Press (Dumbbell)|Lock your legs in. Lower to the lower chest, press up without bouncing.
Chest Press (Machine)|Set the seat so the handles line up with mid-chest. Press without slamming into lockout, return slowly.
Chest Fly (Dumbbell)|Slight bend in the elbows. Open wide until you feel a chest stretch, then hug the weights back up.
Butterfly (Pec Deck)|Back flat on the pad, elbows slightly bent. Squeeze the handles together and return slowly.
Cable Fly Crossovers|Step forward with a slight lean and soft elbows. Sweep the handles together in an arc and control the return.
Push Up|Body in a straight line, hands under the shoulders. Lower your chest to the floor with elbows about 45° out.
Chest Dip|Lean forward with elbows flaring slightly. Lower until shoulders are just below elbows, then press up.
Deadlift (Barbell)|Bar over mid-foot, flat back, lats tight. Push the floor away, keep the bar close, finish by squeezing your glutes.
Pull Up|Overhand grip slightly wider than shoulders. Pull your chest towards the bar, lower to a full hang.
Chin Up|Underhand grip about shoulder-width. Pull your chin over the bar, lower all the way down.
Pull Up (Weighted)|A pull-up with a belt or vest. Log only the added weight.
Lat Pulldown (Cable)|Lean back slightly, pull the bar to the upper chest leading with your elbows, control it back up.
Bent Over Row (Barbell)|Hinge to about 45° with a flat back. Row the bar to the lower ribs and lower under control.
Dumbbell Row|One hand and knee on the bench, flat back. Row the dumbbell to your hip and lower it fully.
Seated Cable Row - V Grip (Cable)|Sit tall, pull the handle to your belly, squeeze the shoulder blades, then let the arms stretch forward.
Seated Row (Machine)|Chest on the pad. Pull your elbows back past the torso, pause, return slowly.
T Bar Row|Hinge with a flat back and row the bar towards your chest without jerking with the hips.
Straight Arm Lat Pulldown (Cable)|Arms nearly straight, slight hip hinge. Sweep the bar down to your thighs using the lats.
Back Extension (Hyperextension)|Hips on the pad. Lower with a neutral spine, rise until your body is in line, don't overarch.
Shrug (Dumbbell)|Arms straight. Lift the shoulders straight up towards the ears, pause, lower fully. No rolling.
Shrug (Barbell)|Hold the bar at your thighs, shrug straight up, pause, lower under control.
Overhead Press (Barbell)|Brace glutes and abs. Press from the collarbone straight up and move your head through at the top.
Shoulder Press (Dumbbell)|Start at ear level, press up and slightly in, don't arch your lower back.
Shoulder Press (Machine)|Handles at shoulder height. Press up without shrugging, lower slowly.
Shoulder Press (Machine Plates)|Handles at shoulder height. Press up without shrugging, lower slowly.
Standing Military Press (Barbell)|Feet together, glutes tight. Press from the collarbone overhead and finish with the bar over mid-foot.
Arnold Press (Dumbbell)|Start with palms facing you, rotate them outward as you press overhead, reverse on the way down.
Lateral Raise (Dumbbell)|Soft elbows. Raise out to the sides to shoulder height leading with the elbows, no swinging.
Lateral Raise (Cable)|Cable from the low pulley across your body. Raise out to shoulder height, lower slowly.
Front Raise (Dumbbell)|Raise in front to shoulder height with soft elbows, lower under control.
Face Pull|Rope at upper-chest height. Pull towards your face with elbows high, rotating the hands back at the end.
Rear Delt Reverse Fly (Dumbbell)|Hinge forward with soft elbows. Raise the arms out to the sides, squeezing the rear shoulders.
Rear Delt Reverse Fly (Machine)|Chest on the pad, arms nearly straight. Sweep back to the sides, return slowly.
Rear Delt Reverse Fly (Cable)|Cross the cables, arms slightly bent. Pull out and back to shoulder level.
Reverse Fly Single Arm (Cable)|Cable across the body at shoulder height. Sweep the arm out and back, control the return.
Upright Row (Barbell)|Shoulder-width grip. Pull up to chest height with elbows leading; stop short if your shoulders pinch.
Bicep Curl (Barbell)|Elbows pinned at your sides. Curl without swinging, lower all the way.
Bicep Curl (Dumbbell)|Elbows still, palms up. Curl and squeeze, then lower slowly to a full stretch.
Bicep Curl (Cable)|Stand close to the low pulley with elbows fixed. Curl up and lower under tension.
EZ Bar Biceps Curl|Grip the angled part, elbows at your sides. Curl up and lower fully.
Hammer Curl (Dumbbell)|Palms facing each other, elbows fixed. Curl up and lower slowly.
Preacher Curl (Barbell)|Armpits snug on the pad. Curl up, lower until the arms are almost straight without bouncing.
Preacher Curl (Machine)|Upper arms flat on the pad. Curl up, lower under control to nearly straight.
Preacher Curl (Dumbbell)|One arm on the pad. Curl up, lower slowly to nearly straight.
Seated Incline Curl (Dumbbell)|Bench at 45–60°, arms hanging back. Curl without letting the elbows drift forward.
Concentration Curl|Elbow braced on the inner thigh. Curl to the shoulder, lower all the way.
Behind the Back Curl (Cable)|Face away from the low pulley with the arm slightly behind you. Curl without moving the elbow forward.
Triceps Pushdown|Elbows at your sides. Push the bar down to full lockout, let it rise to about 90°.
Triceps Rope Pushdown|Elbows fixed. Push down and spread the rope at the bottom, control the return.
Single Arm Triceps Pushdown (Cable)|Elbow pinned to your side. Extend fully, return slowly.
Overhead Triceps Extension (Cable)|Face away from the cable, elbows pointing forward. Extend overhead, stretch at the bottom.
Triceps Extension (Dumbbell)|One dumbbell overhead in both hands. Lower behind your head with elbows in, then extend fully.
Single Arm Tricep Extension (Dumbbell)|Dumbbell overhead, elbow pointing up. Lower behind your head and extend.
Skullcrusher (Barbell)|Lying down, lower the bar to your forehead or just behind it with elbows still, then extend.
Close Grip Bench Press|Hands about shoulder-width, elbows tucked. Lower to the lower chest and press.
Triceps Dip|Body upright. Lower until elbows reach 90°, press up to lockout.
Seated Dip Machine|Sit tall. Push the handles down to lockout, return slowly to 90° at the elbow.
Squat (Barbell)|Brace, sit down between your hips with knees tracking over toes. Reach at least parallel, drive up.
Front Squat|Bar on the front of the shoulders, elbows high. Stay upright, squat deep, drive up.
Hack Squat (Machine)|Back flat on the pad, feet mid-platform. Lower deep with control and press up.
Leg Press (Machine)|Feet shoulder-width. Lower until knees are near 90° without your lower back lifting; don't lock the knees.
Leg Extension (Machine)|Knee lined up with the pivot. Extend fully, pause, lower slowly.
Goblet Squat|One dumbbell held at the chest, elbows inside the knees. Squat deep with your chest up.
Bulgarian Split Squat|Rear foot on a bench. Lower straight down until the back knee nears the floor, push through the front heel.
Walking Lunge (Dumbbell)|Long steps, back knee towards the floor, torso upright. Push through the front foot.
Lunge (Dumbbell)|Step out, lower the back knee towards the floor, push back to standing.
Romanian Deadlift (Barbell)|Soft knees, push the hips back with a flat back. Lower to mid-shin, squeeze the glutes to stand.
Romanian Deadlift (Dumbbell)|Hips back, flat back, dumbbells close to the legs. Feel the hamstrings, stand tall.
Straight Leg Deadlift|Knees almost straight. Hinge at the hips with a flat back as far as your hamstrings allow.
Lying Leg Curl (Machine)|Hips down on the pad. Curl the heels to your glutes, lower slowly.
Seated Leg Curl (Machine)|Thigh pad snug. Curl down fully, control the way up.
Good Morning (Barbell)|Bar on the upper back, soft knees. Hinge forward with a flat back, then stand.
Hip Thrust (Barbell)|Upper back on a bench, bar over the hips. Drive up to a flat torso, squeeze the glutes, chin tucked.
Glute Bridge|Feet flat near the hips. Drive the hips up, squeeze the glutes, lower slowly.
Hip Abduction (Machine)|Sit tall, push the pads out, pause, return slowly.
Hip Adduction (Machine)|Squeeze the pads together, pause, return under control.
Standing Calf Raise (Machine)|Balls of the feet on the edge. Full stretch at the bottom, rise high, pause.
Seated Calf Raise|Pad on the thighs. Lower the heels fully, push up high, pause at the top.
Calf Press (Machine)|On the leg press, push through the balls of the feet over a full, slow range.
Crunch|Lower back on the floor. Curl your ribs towards the hips without pulling on your neck.
Cable Crunch|Kneel with the rope by your head. Crunch down by curling the spine, keep the hips still.
Hanging Leg Raise|Hang still and raise the legs by curling the pelvis up. Lower without swinging.
Lying Leg Raise|Lower back pressed down, legs straight. Lower until just above the floor, then raise.
Ab Wheel|Brace and roll out as far as you can keep a flat back, then pull back with the abs.
Russian Twist (Bodyweight)|Sit back with feet up or down and rotate side to side from the torso.
Sit Up|Feet anchored. Curl up to sitting, lower with control.
Jackknife Sit Up|Lying flat, lift arms and legs together to meet over the hips, lower slowly.
Ab Scissors|Lower back pressed down, legs low. Alternate the legs up and down in small, quick kicks.
Heel Taps|Lying with knees bent, lift the shoulders slightly and reach side to side to tap each heel.
Plank|Forearms under the shoulders, body in a straight line. Squeeze glutes and abs.
Side Plank|Elbow under the shoulder, hips high in a straight line. Hold.
Kettlebell Swing|Hinge and hike the bell back, then snap the hips forward. Arms only guide it to chest height.
Running|Easy cadence, relaxed shoulders, land under your hips.
Treadmill|Set the pace and incline, and avoid holding the rails.
Cycling|Set the seat so your knee is slightly bent at the bottom of the stroke.
Rowing Machine|Drive with the legs, then lean back, then pull with the arms. Reverse that order on the way back.
Elliptical Trainer|Stand tall, push through the whole foot, use the handles lightly.
Walking|Brisk pace, upright posture.
Stair Machine|Stand upright, take full steps, touch the rails lightly.
Stair Machine (Steps)|Stand upright, take full steps, touch the rails lightly.
Jump Rope|Small jumps on the balls of the feet, turn the rope from the wrists.
Lat Pulldown - Close Grip (Cable)|Close-grip handle. Lean back slightly, pull to the upper chest with elbows down and in.
Reverse Grip Lat Pulldown (Cable)|Underhand grip about shoulder-width. Pull to the upper chest, squeeze, control back up.
Single Arm Lat Pulldown|One handle. Pull your elbow down to your side, full stretch at the top.
Lat Pulldown (Machine)|Thighs locked under the pads. Pull to the upper chest, control the return.
Chest Supported Incline Row (Dumbbell)|Chest on a 30–45° bench. Row to the hips and squeeze the shoulder blades."""

fun slug(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

val LIBRARY: List<Exercise> = LIB_TXT.lines().filter { it.isNotBlank() }.map { line ->
    val p = line.split("|")
    Exercise(id = "b:" + slug(p[0]), name = p[0], muscle = p[1], equip = p[2], type = p[3])
}

val CUES: Map<String, String> = CUE_TXT.lines().filter { it.contains("|") }.associate { l ->
    val i = l.indexOf('|'); l.substring(0, i) to l.substring(i + 1)
}

val MUSCLES = listOf("Chest", "Back", "Traps", "Shoulders", "Biceps", "Triceps", "Forearms", "Quads", "Hamstrings", "Glutes", "Calves", "Adductors", "Abs", "Cardio", "Full body", "Other")
val EQUIP = listOf("Barbell", "Dumbbell", "Machine", "Cable", "Kettlebell", "Bodyweight", "Band", "Other", "None")
val GROUP = mapOf("Chest" to "Chest", "Back" to "Back", "Traps" to "Back", "Shoulders" to "Shoulders", "Biceps" to "Arms", "Triceps" to "Arms", "Forearms" to "Arms", "Quads" to "Legs", "Hamstrings" to "Legs", "Glutes" to "Legs", "Calves" to "Legs", "Adductors" to "Legs", "Abs" to "Core", "Cardio" to "Cardio")
val CHIPS = listOf("All", "Chest", "Back", "Shoulders", "Arms", "Legs", "Core", "Cardio", "Other")

data class TypeDef(val f: List<String>, val h: List<String>, val n: String)

val TYPES: Map<String, TypeDef> = linkedMapOf(
    "wr" to TypeDef(listOf("kg", "reps"), listOf("kg", "Reps"), "Weight & reps"),
    "wbw" to TypeDef(listOf("kg", "reps"), listOf("+kg", "Reps"), "Weighted bodyweight"),
    "bw" to TypeDef(listOf("reps"), listOf("Reps"), "Bodyweight reps"),
    "dur" to TypeDef(listOf("sec"), listOf("Sec"), "Duration"),
    "cd" to TypeDef(listOf("km", "min"), listOf("km", "Min"), "Distance & time"),
)
fun typeOf(t: String): TypeDef = TYPES[t] ?: TYPES.getValue("wr")

val PRKEYS = mapOf("wr" to listOf("w", "orm", "vol"), "wbw" to listOf("w", "orm", "vol"), "bw" to listOf("reps"), "dur" to listOf("sec"), "cd" to listOf("km"))
val PRL = mapOf("w" to "Heaviest weight", "orm" to "Best 1RM", "vol" to "Best set volume", "reps" to "Most reps", "sec" to "Longest time", "km" to "Longest distance")
val RESTS = listOf(0, 30, 45, 60, 75, 90, 120, 150, 180, 240, 300)
val SET_TYPES = linkedMapOf("n" to "Normal", "w" to "Warm-up", "d" to "Drop set", "f" to "Failure")

/** Measurement fields: key, label, unit. Same keys as Hevy's measurements export. */
val MF = listOf(
    Triple("weight_kg", "Weight", "kg"), Triple("fat_percent", "Body fat", "%"), Triple("neck_in", "Neck", "in"),
    Triple("shoulder_in", "Shoulders", "in"), Triple("chest_in", "Chest", "in"), Triple("left_bicep_in", "Left bicep", "in"),
    Triple("right_bicep_in", "Right bicep", "in"), Triple("left_forearm_in", "Left forearm", "in"), Triple("right_forearm_in", "Right forearm", "in"),
    Triple("abdomen_in", "Abdomen", "in"), Triple("waist_in", "Waist", "in"), Triple("hips_in", "Hips", "in"),
    Triple("left_thigh_in", "Left thigh", "in"), Triple("right_thigh_in", "Right thigh", "in"), Triple("left_calf_in", "Left calf", "in"),
    Triple("right_calf_in", "Right calf", "in"),
)

/** A reason the value can't be right (a typo like 18232 % body fat), or null. Values are stored in kg, % and inches. */
fun measureProblem(key: String, v: Double): String? {
    val label = MF.firstOrNull { it.first == key }?.second ?: key
    val ok = when {
        key == "weight_kg" -> v in 20.0..400.0
        key == "fat_percent" -> v in 1.0..75.0
        key.endsWith("_in") -> v in 2.0..100.0
        else -> v >= 0
    }
    return if (ok) null else "$label looks wrong. Check the number."
}
