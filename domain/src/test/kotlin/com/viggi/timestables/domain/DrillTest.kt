package com.viggi.timestables.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DrillTest {
    private val random = Random(42)
    private val base = DrillSettings(maxScore = null, idleStopMinutes = null)

    private fun Drill.question(): Question = (phase as Phase.Asking).question
    private fun Drill.answerRight(now: Long) = answer(question().answer, now, random)
    private fun Drill.answerWrong(now: Long) = answer(question().answer + 1, now, random)

    @Test
    fun rightAnswerScoresAPointAndAsksTheNext() {
        val d = Drill.start(base, 0, random).answerRight(1_000)
        assertEquals(1, d.score)
        assertEquals(1, d.right)
        val asking = d.phase as Phase.Asking
        assertEquals(1_000, asking.askedAt)
        assertEquals(1_000, d.attempts.single().millis)
    }

    @Test
    fun wrongAnswerTakesOffThePenaltyButNeverBelowZero() {
        var d = Drill.start(base.copy(wrongPenalty = 2), 0, random)
        d = d.answerRight(1).answerRight(2).answerRight(3) // 3
        d = d.answerWrong(4) // 1
        assertEquals(1, d.score)
        d = d.answerWrong(5) // 0, not -1
        assertEquals(0, d.score)
        d = d.answerRight(6)
        assertEquals(1, d.score)
        assertEquals(2, d.wrong)
    }

    @Test
    fun wrongAnswerLocksTheKeypadForTheWait() {
        var d = Drill.start(base.copy(wrongWaitSeconds = 3), 0, random)
        d = d.answerWrong(1_000)
        val waiting = d.phase as Phase.Waiting
        assertEquals(4_000, waiting.until)
        // An answer during the wait is ignored.
        val ignored = d.answer(waiting.next.answer, 2_000, random)
        assertEquals(1, ignored.attempts.size)
        assertTrue(ignored.phase is Phase.Waiting)
        // After it, the next question is asked from the moment the wait ended.
        d = d.tick(4_500, random)
        val asking = d.phase as Phase.Asking
        assertEquals(waiting.next, asking.question)
        assertEquals(4_000, asking.askedAt)
    }

    @Test
    fun noWaitMeansTheNextQuestionStraightAway() {
        val d = Drill.start(base, 0, random).answerWrong(1_000)
        assertTrue(d.phase is Phase.Asking)
    }

    @Test
    fun unansweredQuestionTimesOutAsWrong() {
        var d = Drill.start(base.copy(answerSeconds = 5, wrongPenalty = 1), 0, random)
        d = d.answerRight(1_000) // score 1, next asked at 1000, due 6000
        d = d.tick(5_999, random)
        assertEquals(1, d.attempts.size)
        d = d.tick(6_000, random)
        assertEquals(1, d.timeouts)
        assertEquals(0, d.score)
        val t = d.attempts.last()
        assertNull(t.given)
        assertEquals(6_000, t.answeredAt)
        assertEquals(6_000, (d.phase as Phase.Asking).askedAt)
    }

    @Test
    fun lateTickCatchesUpEveryTimeoutInOrder() {
        val d = Drill.start(base.copy(answerSeconds = 2), 0, random).tick(7_000, random)
        assertEquals(3, d.timeouts) // at 2s, 4s and 6s
        assertEquals(listOf(2_000L, 4_000L, 6_000L), d.attempts.map { it.answeredAt })
        assertEquals(6_000, (d.phase as Phase.Asking).askedAt)
    }

    @Test
    fun answerAfterTheDeadlineIsTooLate() {
        var d = Drill.start(base.copy(answerSeconds = 5), 0, random)
        val first = d.question()
        d = d.answer(first.answer, 5_500, random)
        assertEquals(Outcome.TIMEOUT, d.attempts[0].outcome)
        // The typed answer went to the next question, asked at 5000.
        assertEquals(2, d.attempts.size)
    }

    @Test
    fun reachingTheMaximumScoreEndsTheSession() {
        var d = Drill.start(base.copy(maxScore = 3), 0, random)
        d = d.answerRight(1).answerRight(2)
        assertTrue(!d.isFinished)
        d = d.answerRight(3)
        assertEquals(Phase.Finished(EndReason.MAX_SCORE, 3), d.phase)
        // Nothing more counts once finished.
        assertEquals(d, d.answer(1, 4, random))
    }

    @Test
    fun sessionTimeLimitEndsItAtTheLimit() {
        var d = Drill.start(base.copy(sessionMinutes = 10), 0, random)
        d = d.answerRight(60_000)
        d = d.tick(700_000, random)
        assertEquals(Phase.Finished(EndReason.TIME_UP, 600_000), d.phase)
    }

    @Test
    fun idleLimitEndsAtTheLastAnswerAndDropsTheTimeoutsAfterIt() {
        var d = Drill.start(base.copy(idleStopMinutes = 1, answerSeconds = 10), 0, random)
        d = d.answerRight(5_000).answerWrong(8_000)
        d = d.tick(68_000, random) // idle since 8000
        assertEquals(Phase.Finished(EndReason.IDLE, 8_000), d.phase)
        assertEquals(listOf(Outcome.RIGHT, Outcome.WRONG), d.attempts.map { it.outcome })
    }

    @Test
    fun pauseStopsEveryClock() {
        var d = Drill.start(base.copy(answerSeconds = 5, sessionMinutes = 1, idleStopMinutes = 1), 0, random)
        d = d.pause(2_000)
        d = d.tick(500_000, random)
        assertTrue(!d.isFinished)
        assertEquals(0, d.attempts.size)
        // Answers while paused are ignored.
        assertEquals(0, d.answer(d.question().answer, 400_000, random).attempts.size)
        d = d.resume(502_000) // a 500 s pause
        assertEquals(500_000, d.pausedMillis)
        assertEquals(505_000L, (d.phase as Phase.Asking).deadline)
        assertEquals(560_000L, d.endsAt)
        d = d.answerRight(503_000)
        assertEquals(3_000, d.attempts.single().millis)
        assertEquals(3_000, d.activeMillis(502_000 + 1_000))
    }

    @Test
    fun stopEndsWhereItIs() {
        val d = Drill.start(base, 0, random).answerRight(1_000).stop(2_000)
        assertEquals(Phase.Finished(EndReason.STOPPED, 2_000), d.phase)
        assertEquals(2_000, d.activeMillis(9_999))
    }

    @Test
    fun deckRunsThroughEveryQuestionBeforeRepeating() {
        val s = base.copy(tablesFrom = 3, tablesTo = 4, timesFrom = 5, timesTo = 6)
        var d = Drill.start(s, 0, random)
        val seen = mutableListOf(d.question())
        repeat(3) { i -> d = d.answerRight(i + 1L); seen += d.question() }
        val facts = seen.map { minOf(it.a, it.b) to maxOf(it.a, it.b) }.toSet()
        assertEquals(setOf(3 to 5, 3 to 6, 4 to 5, 4 to 6), facts)
        // And then it keeps going with a fresh deck.
        d = d.answerRight(10)
        assertNotNull(d.question())
    }

    @Test
    fun settingsAreNormalized() {
        val s = DrillSettings(tablesFrom = 9, tablesTo = 2, maxScore = 0, answerSeconds = 0, wrongPenalty = -1).normalized()
        assertEquals(2, s.tablesFrom)
        assertEquals(9, s.tablesTo)
        assertNull(s.maxScore)
        assertNull(s.answerSeconds)
        assertEquals(0, s.wrongPenalty)
    }
}
