package app.liftlog

import app.liftlog.logic.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolsTest {
    @Test fun platesPerSide() {
        assertEquals(listOf(25.0, 15.0) to 0.0, plates(100.0, 20.0, "kg"))
        assertEquals(listOf(45.0, 45.0) to 0.0, plates(225.0, 45.0, "lb"))
        assertEquals(emptyList<Double>() to 0.0, plates(20.0, 20.0, "kg"))
        assertEquals(listOf(1.25) to 0.5, plates(23.0, 20.0, "kg")) // can't load 23 kg exactly
    }

    @Test fun oneRepMaxAveragesEpleyAndBrzycki() {
        val (avg, ep, br) = oneRm(100.0, 5.0)
        assertEquals(116.7, r1(ep)); assertEquals(112.5, r1(br)); assertEquals(114.6, r1(avg))
        assertEquals(Triple(100.0, 100.0, 100.0), oneRm(100.0, 1.0))
    }

    @Test fun volumeComparisons() {
        assertEquals("About the weight of 2.5 grand pianos", compareWeight(1200.0))
        assertEquals("About the weight of a car", compareWeight(1400.0))
        assertEquals("", compareWeight(5.0))
    }

    @Test fun coachRoutineBlocksAreHiddenFromTheReply() {
        assertEquals("Here you go.", stripRoutines("Here you go.\n```routines\n[{\"name\":\"A\"}]\n```"))
    }
}
