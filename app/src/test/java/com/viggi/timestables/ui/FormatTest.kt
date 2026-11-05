package com.viggi.timestables.ui

import com.viggi.timestables.domain.DrillSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun clock() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:00", formatClock(-500))
        assertEquals("1:05", formatClock(65_999))
        assertEquals("12:00", formatClock(720_000))
        assertEquals("1:02:03", formatClock(3_723_000))
    }

    @Test
    fun seconds() {
        assertEquals("2.3 s", formatSeconds(2_340))
    }

    @Test
    fun ranges() {
        assertEquals("7 × 1–12", rangeText(DrillSettings(tablesFrom = 7, tablesTo = 7, timesFrom = 1, timesTo = 12)))
        assertEquals("2–10 × 1–10", rangeText(DrillSettings()))
    }

    @Test
    fun limits() {
        assertEquals("to 20", limitsText(DrillSettings()))
        assertEquals(
            "10 min · 5 s each · wait 2 s · −1 wrong",
            limitsText(DrillSettings(maxScore = null, sessionMinutes = 10, answerSeconds = 5, wrongWaitSeconds = 2, wrongPenalty = 1)),
        )
        assertEquals("", limitsText(DrillSettings(maxScore = null)))
    }
}
