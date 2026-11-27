package app.liftlog

import app.liftlog.logic.*
import app.liftlog.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineLinkTest {
    private val sled = CustomEx(id = "x_sled", name = "Sled Push", muscle = "Quads", equip = "Machine", type = "dur")
    private val push = Routine(id = "r1", name = "Push Day", exercises = listOf(
        item(BENCH, set(20.0, 10.0, t = "w"), set(60.0, 8.0, done = true, rpe = 8.0), set(60.0, 8.0), rest = 120).copy(note = "Pause on the chest"),
        ExItem(eid = "x_sled", sets = listOf(WSet(sec = 30.0))),
    ))
    private val sender = Index(Data(routines = mapOf("r1" to push), customs = mapOf("x_sled" to sled)))

    @Test fun aLinkCarriesTheRoutineAndOnlyTargets() {
        val link = routineLink(sender, push, lb = false)
        assertTrue(link.startsWith("https://dhammishivam.github.io/lift-log/r/#"))
        val s = parseRoutineLink("Here's my “Push Day” routine on Lift Log: $link")!!
        assertEquals("Push Day", s.n)
        assertEquals(listOf("Bench Press (Barbell)", "Sled Push"), s.x)
        assertEquals(120, s.e[0].rest); assertEquals("Pause on the chest", s.e[0].note)
        assertEquals(listOf("w", "n", "n"), s.e[0].sets.map { it.t })
        assertNull(s.e[0].sets[1].done); assertNull(s.e[0].sets[1].rpe)
        assertEquals("Sled Push", s.c!!.single().name)
        // The app's own deep link and a bare payload work too.
        val payload = link.substringAfter('#')
        assertNotNull(parseRoutineLink("liftlog://r?d=$payload"))
        assertNotNull(parseRoutineLink(payload))
    }

    @Test fun brokenTextIsNotALink() {
        assertNull(parseRoutineLink("hello"))
        assertNull(parseRoutineLink("https://dhammishivam.github.io/lift-log/r/#notbase64!!"))
        assertNull(parseRoutineLink("https://example.com/"))
    }

    @Test fun addingASharedRoutineCreatesMissingCustomExercisesOnce() {
        val s = parseRoutineLink(routineLink(sender, push, lb = false))!!
        var n = 0
        val (r, made) = adoptShared(Index(Data()), s) { "id${n++}" }
        assertEquals(1, made.size); assertEquals("Sled Push", made[0].name); assertEquals("dur", made[0].type)
        assertEquals(listOf(BENCH, made[0].id), r.exercises.map { it.eid })
        assertEquals("Push Day", r.name)
        // Someone who already has "Sled Push" and a "Push Day" keeps theirs and gets "Push Day (shared)".
        val mine = Index(Data(customs = mapOf("x_mine" to sled.copy(id = "x_mine")), routines = mapOf("r9" to push.copy(id = "r9"))))
        val (r2, made2) = adoptShared(mine, s) { "z" }
        assertTrue(made2.isEmpty()); assertEquals("x_mine", r2.exercises[1].eid); assertEquals("Push Day (shared)", r2.name)
    }

    @Test fun namesWithAccentsAndEmojiSurvive() {
        val r = push.copy(name = "Día de empuje 💪")
        assertEquals("Día de empuje 💪", parseRoutineLink(routineLink(sender, r, lb = true))!!.n)
    }
}
