package app.liftlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.*
import app.liftlog.model.*

/** Opens "Find a program", pre-filled with earlier answers and your latest bodyweight. */
fun openPrograms(st: AppState) {
    val w = st.idx.ms.firstOrNull { it.v["weight_kg"] != null }?.v?.get("weight_kg")?.let { plainN(r1(toU(it, st.lb))) } ?: ""
    val prof = st.data.settings.profile
    val h = prof?.heightCm?.let { plainN(if (st.lb) r1(it / CM_PER_IN) else it) } ?: ""
    st.push(Screen.Programs(prof, w, h))
}

private fun Screen.Programs.profile(lb: Boolean) = Profile(
    gender = gender, heightCm = parseNum(height)?.let { if (lb) r1(it * CM_PER_IN) else it },
    exp = exp, equip = equip, goal = goal, days = days,
)

@Composable
fun ProgramsScreen(st: AppState, s: Screen.Programs) {
    val lb = LocalLb.current
    val titles = listOf("About you", "Days a week", "Pick a program")
    Column(Modifier.fillMaxSize()) {
        TopBar(titles[s.step.coerceAtMost(2)], left = { IconBtn(if (s.step == 0) Ic.x else Ic.left, if (s.step == 0) "Close" else "Back", { if (s.step == 0) st.close(s) else s.step-- }) },
            right = { Mu("${minOf(s.step + 1, 3)} of 3", Modifier.padding(end = 12.dp), size = 14.sp) })
        when (s.step) {
            0 -> AboutYou(st, s, lb)
            1 -> DaysStep(s)
            else -> PickStep(st, s, lb)
        }
    }
}

@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, value: String?, onPick: (String) -> Unit) {
    Mu(label, Modifier.padding(top = 14.dp, bottom = 6.dp), size = 14.sp)
    FlowRowX { options.forEach { (k, l) -> Chip(l, value == k) { onPick(k) } } }
}

@Composable
private fun AboutYou(st: AppState, s: Screen.Programs, lb: Boolean) {
    Body {
        Txt("A few quick questions so we can suggest programs that fit you.", size = 16.sp)
        Mu("Only what you know. Everything stays on this phone.", Modifier.padding(top = 4.dp), size = 14.sp)
        Choice("How long have you been lifting?", LEVELS, s.exp) { s.exp = it }
        Choice("Where do you train?", EQUIPS, s.equip) { s.equip = it }
        Choice("Main goal", GOALS, s.goal) { s.goal = it }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Bodyweight (${unitOf(lb)})", s.weight, { s.weight = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            Field("Height (${if (lb) "in" else "cm"})", s.height, { s.height = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
        }
        Choice("Gender (optional)", GENDERS, s.gender) { s.gender = if (s.gender == it) null else it }
        Btn("Next", {
            when {
                s.exp == null -> st.say("Pick how long you've been lifting")
                s.equip == null -> st.say("Pick where you train")
                else -> {
                    parseNum(s.weight)?.let { w -> measureProblem("weight_kg", fromU(w, lb))?.let { m -> st.say(m); return@Btn } }
                    s.step = 1
                }
            }
        }, Modifier.fillMaxWidth().padding(top = 24.dp), kind = BtnKind.Primary)
    }
}

@Composable
private fun DaysStep(s: Screen.Programs) {
    val p = LocalPal.current
    val hints = mapOf(2 to "Busy weeks. Full body twice.", 3 to "The most popular. Full body or a split.", 4 to "Upper and lower body, twice each.", 5 to "A split with room for every muscle.", 6 to "For experienced lifters who love the gym.")
    Body {
        Txt("How many days a week can you train?", size = 18.sp, weight = FontWeight.SemiBold)
        Mu("Pick what you can keep up. You can change program later.", Modifier.padding(top = 4.dp, bottom = 12.dp), size = 14.sp)
        (2..6).forEach { n ->
            val on = s.days == n
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(if (on) p.acs else p.s1)
                .then(if (on) Modifier.border(2.dp, p.ac, RoundedCornerShape(14.dp)) else Modifier)
                .clickable { s.days = n; s.step = 2 }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Big("$n", size = 28.sp); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) { Txt("$n days a week", weight = FontWeight.SemiBold); Mu(hints.getValue(n), size = 14.sp) }
                Icon(Ic.right, null, Modifier.size(20.dp), tint = p.mu)
            }
        }
    }
}

@Composable
private fun PickStep(st: AppState, s: Screen.Programs, lb: Boolean) {
    val p = LocalPal.current
    val list = recommend(s.profile(lb), s.days ?: 3)
    Body {
        Mu("Suggested for ${s.days} days a week, ${equipLabel(s.equip ?: "gym").lowercase()}.", Modifier.padding(bottom = 8.dp), size = 15.sp)
        list.forEachIndexed { i, sg ->
            val g = sg.program
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(p.s1)
                .then(if (i == 0) Modifier.border(2.dp, p.ac, RoundedCornerShape(16.dp)) else Modifier)
                .clickable { addProgram(st, s, g, s.profile(lb), lb) }.padding(16.dp)) {
                if (i == 0) Txt("Best match", Modifier.padding(bottom = 4.dp), size = 13.sp, weight = FontWeight.Bold, color = p.ac)
                Txt(g.name, size = 18.sp, weight = FontWeight.SemiBold)
                Mu("${g.days} days · ${levelLabel(g.level)} · ${equipLabel(g.equip)}", Modifier.padding(top = 2.dp), size = 14.sp)
                if (sg.note != null) Txt(sg.note, Modifier.padding(top = 2.dp), size = 13.sp, color = p.wu)
                Txt(g.about, Modifier.padding(top = 8.dp), size = 15.sp)
                Mu(g.plan.joinToString(" · ") { it.name }, Modifier.padding(top = 6.dp), size = 13.sp)
            }
        }
    }
}

private fun addProgram(st: AppState, s: Screen.Programs, g: Program, prof: Profile, lb: Boolean) {
    fun go() {
        st.setSettings { it.copy(profile = prof, goal = it.goal?.takeIf { n -> n > 0 } ?: g.days) }
        parseNum(s.weight)?.let { w ->
            val kg = r2(fromU(w, lb)); val today = startOfDay(ldt(nowMs()).date)
            val last = st.idx.ms.firstOrNull { it.v["weight_kg"] != null }
            if (last == null || last.date < today || last.v["weight_kg"] != kg) {
                val m = st.idx.ms.firstOrNull { it.date >= today }
                st.store.putMeasure(m?.copy(v = m.v + ("weight_kg" to kg)) ?: Measure(id = "m_" + rid(), date = today + 12 * 3_600_000L, v = mapOf("weight_kg" to kg)))
            }
        }
        val rs = programRoutines(g, prof, st.idx, nowMs())
        rs.forEach { st.store.putRoutine(it) }
        st.fold.keys.toList().forEach { k -> if (k != g.name) st.fold[k] = true }
        st.fold[g.name] = false
        st.close(s); st.tab = Tab.Workout
        // Straight to the Workout tab: the routines are there to start. Undo takes them away again.
        st.say("${g.name} added") { rs.forEach { st.store.deleteRoutine(it.id) } }
    }
    if (st.idx.rs.any { it.folder == g.name }) st.confirm("You already have ${g.name}", "Add another copy of its routines?", "Add again") { go() } else go()
}
