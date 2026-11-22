package app.liftlog

import app.liftlog.logic.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {
    @Test fun numbersAreGroupedAndTrimmed() {
        assertEquals("12,450", fmtN(12450.0))
        assertEquals("62.5", fmtN(62.5))
        assertEquals("1.25", fmtN(1.25))
        assertEquals("0", fmtN(0.0))
        assertEquals("-2.5", fmtN(-2.5))
        assertEquals("", fmtN(null))
    }

    @Test fun plainNumbersForInputs() {
        assertEquals("100", plainN(100.0))
        assertEquals("72.5", plainN(72.5))
        assertEquals("", plainN(null))
    }

    @Test fun parsingAcceptsCommasAndRejectsJunk() {
        assertEquals(62.5, parseNum("62,5"))
        assertEquals(10.0, parseNum(" 10 "))
        assertNull(parseNum(""))
        assertNull(parseNum("abc"))
        assertNull(parseNum(null))
    }

    @Test fun durationsAndRest() {
        assertEquals("1:30", fmtSec(90.0))
        assertEquals("1:01:05", fmtSec(3665.0))
        assertEquals("1h 5m", dur(65 * 60_000L))
        assertEquals("Off", fmtRest(0))
        assertEquals("45s", fmtRest(45))
        assertEquals("2min", fmtRest(120))
        assertEquals("1min 30s", fmtRest(90))
    }

    @Test fun hevyDatesRoundTrip() {
        val t = at(2026, 11, 21, 19, 41)
        assertEquals("21 Nov 2026, 19:41", hevyDate(t))
        assertEquals(t, parseDate(hevyDate(t)))
        assertEquals(at(2024, 9, 4, 7, 12), parseDate("4 Sept 2024, 07:12"))
        assertNull(parseDate("not a date"))
    }

    @Test fun unitsConvert() {
        assertEquals(220.46, r2(toU(100.0, true)))
        assertEquals(100.0, r2(fromU(toU(100.0, true), true)))
        assertEquals("100 kg", wt(100.0, false))
    }

    @Test fun setFormattingByType() {
        assertEquals("80 kg × 8 @8", fmtSet("wr", set(80.0, 8.0, rpe = 8.0), false))
        assertEquals("12 reps", fmtSet("bw", set(null, 12.0), false))
        assertEquals("1:30", fmtSet("dur", app.liftlog.model.WSet(sec = 90.0), false))
    }

    @Test fun setLabelsCountOnlyNormalSets() {
        val sets = listOf(set(40.0, 10.0, t = "w"), set(80.0, 8.0), set(80.0, 8.0), set(60.0, 10.0, t = "d"))
        assertEquals(listOf("W", "1", "2", "D"), sets.indices.map { setLabel(sets, it) })
    }
}
