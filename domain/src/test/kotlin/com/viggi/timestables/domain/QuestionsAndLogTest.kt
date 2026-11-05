package com.viggi.timestables.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionsAndLogTest {

    @Test
    fun deckHoldsEveryPairInTheRanges() {
        val s = DrillSettings(tablesFrom = 7, tablesTo = 7, timesFrom = 1, timesTo = 12)
        val all = QuestionDeck.all(s)
        assertEquals(12, all.size)
        assertTrue(all.all { it.a == 7 })
        val shuffled = QuestionDeck.shuffled(s, Random(1))
        assertEquals(12, shuffled.size)
        assertEquals((1..12).toSet(), shuffled.map { if (it.a == 7) it.b else it.a }.toSet())
    }

    @Test
    fun freshDeckDoesNotRepeatTheLastFact() {
        val s = DrillSettings(tablesFrom = 2, tablesTo = 3, timesFrom = 2, timesTo = 2)
        repeat(50) { seed ->
            val deck = QuestionDeck.shuffled(s, Random(seed), after = Question(2, 2))
            assertFalse(deck[0].sameFactAs(Question(2, 2)))
        }
    }

    @Test
    fun aDeckOfOneStillDeals() {
        val s = DrillSettings(tablesFrom = 5, tablesTo = 5, timesFrom = 5, timesTo = 5)
        assertEquals(listOf(Question(5, 5)), QuestionDeck.shuffled(s, Random(0), after = Question(5, 5)))
    }

    private fun record() = SessionRecord(
        id = "abc",
        settings = DrillSettings(tablesFrom = 3, maxScore = null, answerSeconds = 8, wrongPenalty = 1),
        startedAt = 1_000,
        endedAt = 61_000,
        reason = EndReason.TIME_UP,
        attempts = listOf(
            Attempt(Question(3, 4), 12, Outcome.RIGHT, 1_000, 3_000),
            Attempt(Question(4, 7), 27, Outcome.WRONG, 3_000, 7_000),
            Attempt(Question(7, 4), null, Outcome.TIMEOUT, 7_000, 15_000),
            Attempt(Question(6, 6), 36, Outcome.RIGHT, 15_000, 19_000),
        ),
        pausedMillis = 10_000,
    )

    @Test
    fun logLineRoundTrips() {
        val r = record()
        val line = SessionCodec.encode(r)
        assertFalse(line.contains('\n'))
        assertEquals(r, SessionCodec.decode(line))
    }

    @Test
    fun badLinesReadAsNothing() {
        assertNull(SessionCodec.decode(""))
        assertNull(SessionCodec.decode("{not json"))
        assertNull(SessionCodec.decode("""{"id":"x"}"""))
    }

    @Test
    fun missingSettingsKeepTheirDefaults() {
        val s = SessionCodec.decodeSettingsString("""{"tablesFrom":4,"maxScore":null}""")
        assertEquals(4, s.tablesFrom)
        assertNull(s.maxScore)
        assertEquals(DrillSettings().timesTo, s.timesTo)
        assertEquals(DrillSettings(), SessionCodec.decodeSettingsString(null))
        assertEquals(DrillSettings(), SessionCodec.decodeSettingsString("garbage"))
    }

    @Test
    fun recordTotals() {
        val r = record()
        assertEquals(1, r.score) // 1, 0, 0, 1
        assertEquals(2, r.right)
        assertEquals(1, r.wrong)
        assertEquals(1, r.timeouts)
        assertEquals(50_000, r.activeMillis)
        assertEquals(3_000L, r.averageRightMillis)
    }

    @Test
    fun missedFactsCountBothWaysRoundTogether() {
        val facts = missedFacts(listOf(record()))
        assertEquals(listOf(FactStats(4, 7, asked = 2, missed = 2)), facts)
    }

    @Test
    fun sessionWithNoAnswersIsNotLogged() {
        val d = Drill.start(DrillSettings(answerSeconds = 1, idleStopMinutes = null), 0, Random(0))
            .tick(3_500, Random(0)).stop(3_500)
        assertEquals(3, d.timeouts)
        assertNull(SessionRecord.of(d, "x"))
    }

    @Test
    fun tonesAreShortLoudAndNeverClip() {
        for (t in listOf(Tones.right(), Tones.wrong(), Tones.timeout(), Tones.fanfare())) {
            assertTrue(t.size in Tones.SAMPLE_RATE / 10..Tones.SAMPLE_RATE * 2)
            assertTrue(t.maxOf { it.toInt() } > Short.MAX_VALUE / 4)
            assertTrue(t.all { it > Short.MIN_VALUE })
        }
    }
}
