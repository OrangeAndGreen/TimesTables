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

    @Test
    fun divisionIsTheFactTurnedRound() {
        val q = Question(21, 7, Op.DIVIDE)
        assertEquals(3, q.answer)
        assertEquals("21 ÷ 7", q.toString())
        assertEquals(3 to 7, q.fact)
        assertTrue(q.sameFactAs(Question(7, 3)))
        assertTrue(q.sameFactAs(Question(21, 3, Op.DIVIDE)))
        assertNull(Question.division(5, 0, divisor = 0))
    }

    @Test
    fun divisionDeckAsksEveryFactAsAWholeDivision() {
        val s = DrillSettings(tablesFrom = 7, tablesTo = 7, timesFrom = 1, timesTo = 12, operations = Operations.DIVIDE)
        assertEquals((1..12).map { Question(7 * it, 7, Op.DIVIDE) }, QuestionDeck.all(s))
        repeat(20) { seed ->
            val deck = QuestionDeck.shuffled(s, Random(seed))
            assertEquals(12, deck.size)
            assertTrue(deck.all { it.op == Op.DIVIDE && it.a == it.b * it.answer })
            assertEquals((1..12).map { 7 to it }.map { minOf(it.first, it.second) to maxOf(it.first, it.second) }.toSet(),
                deck.map { it.fact }.toSet())
            // Turned round at random: some divide by 7, some into 7.
        }
        val divisors = (0 until 20).flatMap { QuestionDeck.shuffled(s, Random(it)) }.map { it.b }.toSet()
        assertTrue(7 in divisors && divisors.size > 1)
    }

    @Test
    fun divisionNeverDividesByZero() {
        val s = DrillSettings(tablesFrom = 0, tablesTo = 2, timesFrom = 0, timesTo = 2, operations = Operations.DIVIDE)
        // Every pair but 0 × 0, which can't be turned into a division.
        assertEquals(8, QuestionDeck.all(s).size)
        repeat(50) { seed -> assertTrue(QuestionDeck.shuffled(s, Random(seed)).all { it.b != 0 }) }
    }

    @Test
    fun divisionWithNothingToDivideByFallsBackToMultiplying() {
        val s = DrillSettings(tablesFrom = 0, tablesTo = 0, timesFrom = 0, timesTo = 0, operations = Operations.DIVIDE)
        assertEquals(listOf(Question(0, 0)), QuestionDeck.all(s))
    }

    @Test
    fun bothAsksEachFactEachWayWithoutGivingItAway() {
        val s = DrillSettings(tablesFrom = 2, tablesTo = 4, timesFrom = 2, timesTo = 4, operations = Operations.BOTH)
        assertEquals(18, QuestionDeck.all(s).size)
        repeat(100) { seed ->
            val deck = QuestionDeck.shuffled(s, Random(seed), after = Question(3, 3))
            assertEquals(9, deck.count { it.op == Op.TIMES })
            assertEquals(9, deck.count { it.op == Op.DIVIDE })
            assertFalse(deck[0].sameFactAs(Question(3, 3)))
            for (i in 1 until deck.size) assertFalse("seed $seed at $i: $deck", deck[i].sameFactAs(deck[i - 1]))
        }
    }

    private fun record() = SessionRecord(
        id = "abc",
        settings = DrillSettings(tablesFrom = 3, maxScore = null, answerSeconds = 8, wrongPenalty = 1, operations = Operations.BOTH),
        startedAt = 1_000,
        endedAt = 61_000,
        reason = EndReason.TIME_UP,
        attempts = listOf(
            Attempt(Question(3, 4), 12, Outcome.RIGHT, 1_000, 3_000),
            Attempt(Question(4, 7), 27, Outcome.WRONG, 3_000, 7_000),
            Attempt(Question(7, 4), null, Outcome.TIMEOUT, 7_000, 15_000),
            Attempt(Question(6, 6), 36, Outcome.RIGHT, 15_000, 19_000),
            Attempt(Question(28, 7, Op.DIVIDE), 5, Outcome.WRONG, 19_000, 22_000),
            Attempt(Question(28, 4, Op.DIVIDE), 7, Outcome.RIGHT, 22_000, 25_000),
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
        assertEquals(1, r.score) // 1, 0, 0, 1, 0, 1
        assertEquals(3, r.right)
        assertEquals(2, r.wrong)
        assertEquals(1, r.timeouts)
        assertEquals(50_000, r.activeMillis)
        assertEquals(3_000L, r.averageRightMillis)
    }

    @Test
    fun missedFactsCountBothWaysRoundTogether() {
        val facts = missedFacts(listOf(record()))
        assertEquals(
            listOf(FactStats(4, 7, asked = 2, missed = 2), FactStats(4, 7, asked = 2, missed = 1, op = Op.DIVIDE)),
            facts,
        )
    }

    @Test
    fun oldLogLinesReadAsMultiplication() {
        val line = """{"id":"old","startedAt":0,"endedAt":10,"reason":"STOPPED","settings":{"tablesFrom":3},""" +
            """"attempts":[{"a":3,"b":4,"given":12,"outcome":"RIGHT","askedAt":0,"answeredAt":5}]}"""
        val r = SessionCodec.decode(line)!!
        assertEquals(Operations.MULTIPLY, r.settings.operations)
        assertEquals(Question(3, 4), r.attempts.single().question)
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
