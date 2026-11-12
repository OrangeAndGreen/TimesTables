package com.viggi.timestables.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FactWeightsTest {

    private fun session(startedAt: Long, vararg attempts: Pair<Question, Boolean>) = SessionRecord(
        id = "s$startedAt",
        settings = DrillSettings(),
        startedAt = startedAt,
        endedAt = startedAt + 1_000,
        reason = EndReason.STOPPED,
        attempts = attempts.map { (q, right) ->
            Attempt(q, if (right) q.answer else q.answer + 1, if (right) Outcome.RIGHT else Outcome.WRONG, startedAt, startedAt)
        },
    )

    /** Every fact in the log asked many times, 7 × 8 missed in nearly all of them. */
    private fun historyMissing7x8(): List<SessionRecord> = (0 until 6).map { i ->
        session(
            i * 1_000L,
            Question(7, 8) to false, Question(8, 7) to false, Question(7, 8) to (i == 0),
            Question(3, 4) to true, Question(6, 6) to true,
        )
    }

    @Test
    fun noHistoryDealsExactlyThePlainDeck() {
        val s = DrillSettings(operations = Operations.BOTH)
        val weights = FactWeights.from(emptyList())
        repeat(20) { seed ->
            assertEquals(
                QuestionDeck.shuffled(s, Random(seed)),
                QuestionDeck.shuffled(s, Random(seed), weights = weights),
            )
        }
    }

    @Test
    fun factsNeverMissedOrNeverAskedKeepTheBaseWeight() {
        val w = FactWeights.from(historyMissing7x8())
        assertEquals(FactWeights.BASE_WEIGHT, w.of(Question(3, 4)), 0.0)
        assertEquals(FactWeights.BASE_WEIGHT, w.of(Question(9, 9)), 0.0)
        assertTrue(w.of(Question(7, 8)) > 3.0)
        assertEquals(w.of(Question(7, 8)), w.of(Question(8, 7)), 0.0)
    }

    @Test
    fun noWeightIsZeroAndNoneTopsTheCap() {
        val everythingMissed = (0 until 30).map { i ->
            session(i.toLong(), *(2..10).flatMap { t -> (1..10).map { x -> Question(t, x) to false } }.toTypedArray())
        }
        for (history in listOf(emptyList(), historyMissing7x8(), everythingMissed)) {
            val w = FactWeights.from(history)
            for (q in QuestionDeck.all(DrillSettings(operations = Operations.BOTH))) {
                assertTrue(w.of(q) >= FactWeights.BASE_WEIGHT)
                assertTrue(w.of(q) <= FactWeights.MAX_WEIGHT)
                assertTrue(w.copies(q, Random(0)) >= 1)
            }
        }
    }

    @Test
    fun oneUnluckyMissCountsLessThanAHabit() {
        val once = FactWeights.from(listOf(session(0, Question(7, 8) to false)))
        val habit = FactWeights.from(historyMissing7x8())
        assertTrue(once.of(Question(7, 8)) < habit.of(Question(7, 8)))
    }

    @Test
    fun recentSessionsCountMoreThanOldOnes() {
        val missedLongAgo = listOf(session(0, Question(7, 8) to false)) +
            (1..10).map { session(it * 1_000L, Question(7, 8) to true) }
        val missedLately = listOf(session(20_000, Question(7, 8) to false)) +
            (1..10).map { session(it * 1_000L, Question(7, 8) to true) }
        assertTrue(
            FactWeights.from(missedLately).of(Question(7, 8)) > FactWeights.from(missedLongAgo).of(Question(7, 8)),
        )
    }

    @Test
    fun divisionsAreWeightedApartFromTheMultiplication() {
        val w = FactWeights.from(listOf(session(0, Question(56, 7, Op.DIVIDE) to false, Question(7, 8) to true)))
        assertTrue(w.of(Question(56, 7, Op.DIVIDE)) > FactWeights.BASE_WEIGHT)
        assertEquals(w.of(Question(56, 7, Op.DIVIDE)), w.of(Question(56, 8, Op.DIVIDE)), 0.0)
        assertEquals(FactWeights.BASE_WEIGHT, w.of(Question(7, 8)), 0.0)
        assertEquals(FactWeights.BASE_WEIGHT, w.of(Question(8, 7)), 0.0)
    }

    @Test
    fun aMissedFactComesUpNoticeablyMoreInALongSeededRun() {
        val s = DrillSettings(maxScore = null, idleStopMinutes = null)
        val weights = FactWeights.from(historyMissing7x8())
        val random = Random(7)
        var d = Drill.start(s, 0, random, weights)
        repeat(2_000) { i ->
            val q = (d.phase as Phase.Asking).question
            d = d.answer(q.answer, (i + 1) * 1_000L, random)
        }
        val counts = d.attempts.groupingBy { it.question.fact }.eachCount()
        val missed = counts.getValue(7 to 8)
        val typical = counts.filterKeys { it != 7 to 8 && it.first != it.second }.values.average()
        // Each pass asks 7 × 8 about as often as its weight, every other fact once (or twice for a 3 × 4 / 4 × 3 pair).
        assertTrue("7 × 8 asked $missed times, a typical fact $typical", missed > typical * 1.5)
        // Every fact in the range still comes up.
        assertEquals(QuestionDeck.all(s).map { it.fact }.toSet(), counts.keys)
    }

    @Test
    fun copiesOfAMissedFactAreNeverBackToBack() {
        val history = listOf(
            session(0, Question(7, 8) to false, Question(56, 7, Op.DIVIDE) to false, Question(3, 7) to false),
            session(1, Question(7, 8) to false, Question(56, 8, Op.DIVIDE) to false, Question(7, 3) to false),
        )
        val weights = FactWeights.from(history)
        val s = DrillSettings(tablesFrom = 7, tablesTo = 7, timesFrom = 1, timesTo = 12, operations = Operations.BOTH)
        repeat(200) { seed ->
            val deck = QuestionDeck.shuffled(s, Random(seed), after = Question(8, 7), weights = weights)
            assertTrue(deck.size > QuestionDeck.all(s).size)
            assertFalse("seed $seed opens on the fact just asked", deck.first().sameFactAs(Question(8, 7)))
            deck.zipWithNext().forEach { (x, y) -> assertFalse("seed $seed: $x then $y", x.sameFactAs(y)) }
            // Every question, both ways, still comes at least once a pass.
            assertEquals(QuestionDeck.all(s).map { it.op to it.fact }.toSet(), deck.map { it.op to it.fact }.toSet())
        }
    }

    @Test
    fun practiceSettingIsRememberedAndOffInOldEntries() {
        val on = DrillSettings(practiceMissed = true)
        assertEquals(on, SessionCodec.decodeSettingsString(SessionCodec.encodeSettingsString(on)))
        assertFalse(SessionCodec.decodeSettingsString("""{"tablesFrom":3}""").practiceMissed)
        assertFalse(DrillSettings().practiceMissed)
    }
}
