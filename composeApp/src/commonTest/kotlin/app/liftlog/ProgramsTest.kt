package app.liftlog

import app.liftlog.logic.*
import app.liftlog.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramsTest {
    private val idx = Index(Data())

    @Test fun catalogueParsesAndUsesLibraryExercises() {
        assertEquals(11, PROGRAMS.size)
        assertEquals(PROGRAMS.size, PROGRAMS.map { it.id }.toSet().size)
        PROGRAMS.forEach { g ->
            assertTrue(g.plan.isNotEmpty(), g.id)
            g.plan.forEach { d -> d.exercises.forEach { e -> assertTrue(idx.ex.containsKey("b:" + slug(e.name)), "${g.id}: ${e.name} isn't in the library") } }
        }
        assertEquals(listOf("Strength A", "Strength B"), PROGRAMS.first { it.id == "sl5" }.plan.map { it.name })
    }

    @Test fun suggestionsFitEquipmentDaysAndLevel() {
        assertTrue(recommend(Profile(exp = "new", equip = "gym", goal = "muscle"), 3).first().program.id == "fb3")
        assertEquals("sl5", recommend(Profile(exp = "new", equip = "gym", goal = "strength"), 3).first().program.id)
        assertEquals("ppl6", recommend(Profile(exp = "exp", equip = "gym"), 6).first().program.id)
        assertEquals("ul4", recommend(Profile(exp = "some", equip = "gym"), 4).first().program.id)
        // Dumbbells only: never a barbell program; no-equipment: only bodyweight.
        val db = recommend(Profile(exp = "some", equip = "dumbbell"), 4)
        assertEquals("dbul4", db.first().program.id); assertTrue(db.all { it.program.equip != "gym" })
        val bw = recommend(Profile(exp = "new", equip = "none"), 5)
        assertEquals(listOf("bw3"), bw.map { it.program.id })
        assertTrue(bw.first().note!!.contains("closest"))
    }

    @Test fun programBecomesAFolderOfRoutines() {
        val g = PROGRAMS.first { it.id == "fb3" }
        val rs = programRoutines(g, Profile(exp = "new"), idx, 1000L)
        assertEquals(listOf("Full Body A", "Full Body B", "Full Body C"), rs.map { it.name })
        assertTrue(rs.all { it.folder == "Full Body 3-Day" })
        assertEquals(listOf(1000L, 1001L, 1002L), rs.map { it.created })
        val plank = rs[1].exercises.first { it.eid == "b:" + slug("Plank") }
        assertEquals(45.0, plank.sets[0].sec); assertEquals(60, plank.rest)
        // New lifters get at most 3 sets.
        val ppl = programRoutines(PROGRAMS.first { it.id == "ppl3" }, Profile(exp = "new"), idx, 0L)
        assertTrue(ppl.all { r -> r.exercises.all { it.sets.size <= 3 } })
        assertEquals(4, programRoutines(PROGRAMS.first { it.id == "ppl3" }, Profile(exp = "exp"), idx, 0L)[0].exercises[0].sets.size)
    }

    @Test fun nextRoutineStaysInTheProgram() {
        val a = Routine(id = "a", name = "Strength A", folder = "5×5 Strength", created = 1)
        val b = Routine(id = "b", name = "Strength B", folder = "5×5 Strength", created = 2)
        val other = Routine(id = "o", name = "Arms", created = 3)
        val did = workout("w1", at(2026, 3, 2), item(BENCH, set(60.0, 5.0))).copy(routineId = "b")
        assertEquals("a", nextRoutine(Index(data(did, routines = listOf(a, b, other))))?.id)
    }
}
