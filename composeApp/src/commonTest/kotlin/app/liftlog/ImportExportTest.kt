package app.liftlog

import app.liftlog.logic.*
import app.liftlog.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportExportTest {
    private val empty = Index(Data())

    @Test fun csvHandlesQuotesCommasAndBlankLines() {
        val rows = parseCsv("a,b\n\"x, y\",\"say \"\"hi\"\"\"\n\n1,2\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("x, y", "say \"hi\""), listOf("1", "2")), rows)
    }

    @Test fun hevyWorkoutsImport() {
        val r = parseHevyWorkouts(HEVY_WORKOUTS, empty)
        assertEquals(2, r.workouts.size)
        val push = r.workouts.first { it.title == "Push" }
        assertEquals("Felt strong, \"easy\" day", push.notes)
        assertEquals(at(2026, 1, 4, 7, 12), push.start)
        assertEquals(at(2026, 1, 4, 8, 20), push.end)
        assertEquals("hevy", push.src)

        val bench = push.exercises[0]
        assertEquals(BENCH, bench.eid)
        assertEquals("Pause reps", bench.note)
        assertEquals(listOf("w", "n", "d"), bench.sets.map { it.t })
        assertEquals(8.0, bench.sets[1].rpe)

        // Pull Up and the unknown exercise share superset 0; the unknown one becomes a custom exercise.
        assertEquals(PULLUP, push.exercises[1].eid)
        assertEquals(0, push.exercises[1].ss)
        assertEquals(1, r.customs.size)
        assertEquals("Cable Crunch Deluxe", r.customs[0].name)
        assertEquals("wr", r.customs[0].type)
        assertEquals("f", push.exercises[2].sets[0].t)

        // "Sept" month spelling and timed sets.
        val legs = r.workouts.first { it.title == "Legs" }
        assertEquals(PLANK, legs.exercises[0].eid)
        assertEquals(90.0, legs.exercises[0].sets[0].sec)
    }

    @Test fun reimportingGivesTheSameIds() {
        val a = parseHevyWorkouts(HEVY_WORKOUTS, empty).workouts.map { it.id }
        val b = parseHevyWorkouts(HEVY_WORKOUTS, empty).workouts.map { it.id }
        assertEquals(a, b)
    }

    @Test fun knownCustomExercisesAreNotDuplicated() {
        val first = parseHevyWorkouts(HEVY_WORKOUTS, empty)
        val idx = Index(Data(customs = first.customs.associateBy { it.id }))
        assertTrue(parseHevyWorkouts(HEVY_WORKOUTS, idx).customs.isEmpty())
    }

    @Test fun poundsColumnIsConverted() {
        val csv = "title,start_time,end_time,exercise_title,set_type,weight_lbs,reps\nA,\"1 Feb 2026, 10:00\",\"1 Feb 2026, 11:00\",Bench Press (Barbell),normal,225,5"
        val w = parseHevyWorkouts(csv, empty).workouts.single()
        assertEquals(102.06, w.exercises[0].sets[0].kg)
    }

    @Test fun notAHevyFileIsRejected() {
        assertFailsWith<IllegalArgumentException> { parseHevyWorkouts("name,age\nbob,3", empty) }
        assertFailsWith<IllegalArgumentException> { parseHevyMeasures("foo,bar\n1,2") }
    }

    @Test fun hevyMeasurementsImportSkipsEmptyRows() {
        val ms = parseHevyMeasures(HEVY_MEASURES)
        assertEquals(2, ms.size)
        assertEquals(78.4, ms[0].v["weight_kg"])
        assertEquals(18.5, ms[0].v["fat_percent"])
        assertNull(ms[1].v["fat_percent"])
    }

    @Test fun workoutCsvExportReimportsToTheSameSets() {
        val imported = parseHevyWorkouts(HEVY_WORKOUTS, empty)
        val idx = Index(Data(workouts = imported.workouts.associateBy { it.id }, customs = imported.customs.associateBy { it.id }))
        val again = parseHevyWorkouts(csvWorkouts(idx), idx).workouts.sortedBy { it.start }
        val orig = imported.workouts.sortedBy { it.start }
        assertEquals(orig.map { it.title to it.start }, again.map { it.title to it.start })
        assertEquals(orig.flatMap { w -> w.exercises.map { it.eid to it.sets } }, again.flatMap { w -> w.exercises.map { it.eid to it.sets } })
    }

    @Test fun measurementCsvExportReimports() {
        val ms = parseHevyMeasures(HEVY_MEASURES)
        val idx = Index(Data(measures = ms.associateBy { it.id }))
        assertEquals(ms.map { it.date to it.v }.sortedBy { it.first }, parseHevyMeasures(csvMeasures(idx)).map { it.date to it.v }.sortedBy { it.first })
    }

    @Test fun backupRoundTripLeavesOutTheApiKey() {
        val d = data(workout("w1", at(2026, 3, 1), item(BENCH, set(80.0, 8.0))),
            settings = Settings(unit = "lb", apiKey = "sk-ant-secret", theme = "light"))
        val json = backupJson(d)
        assertTrue("sk-ant-secret" !in json)
        val docs = parseBackup(json)
        assertEquals(setOf("w1", "settings", "pins"), docs.map { it.first }.toSet())
        val s = JsonX.decodeFromJsonElement(Settings.serializer(), docs.first { it.first == "settings" }.third)
        assertEquals("lb", s.unit); assertEquals("light", s.theme); assertNull(s.apiKey)
    }

    @Test fun backupWithoutDocsIsRejected() {
        assertFailsWith<IllegalArgumentException> { parseBackup("{\"app\":\"liftlog\"}") }
    }
}
