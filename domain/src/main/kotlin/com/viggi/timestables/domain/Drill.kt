package com.viggi.timestables.domain

import kotlin.random.Random

enum class Outcome { RIGHT, WRONG, TIMEOUT }

/** One answer, or one question left until its time ran out. Times are epoch milliseconds. */
data class Attempt(
    val question: Question,
    /** What was typed, or null when the time ran out. */
    val given: Int?,
    val outcome: Outcome,
    val askedAt: Long,
    val answeredAt: Long,
) {
    val millis: Long get() = answeredAt - askedAt
}

enum class EndReason {
    /** Reached the maximum score: the congratulations screen. */
    MAX_SCORE,
    /** The session's own time limit ran out. */
    TIME_UP,
    /** Stopped with the stop button. */
    STOPPED,
    /** Nothing typed for the idle limit; the session ends at the last answer. */
    IDLE,
}

sealed interface Phase {
    /** Showing [question] and waiting for an answer, until [deadline] when there is one. */
    data class Asking(val question: Question, val askedAt: Long, val deadline: Long?) : Phase

    /** The keypad is locked after a wrong answer until [until]; [next] comes after. */
    data class Waiting(val missed: Attempt, val until: Long, val next: Question) : Phase

    data class Finished(val reason: EndReason, val at: Long) : Phase
}

/** The score after every attempt in turn: one for a right answer, the penalty off for a wrong one, never below zero. */
fun runningScore(attempts: List<Attempt>, wrongPenalty: Int): Int =
    attempts.fold(0) { score, a ->
        if (a.outcome == Outcome.RIGHT) score + 1 else (score - wrongPenalty).coerceAtLeast(0)
    }

/**
 * A session in progress, as an immutable value. Every change is a function of the current
 * value and the time, so the rules are tested without clocks or threads. The caller keeps
 * one [Random] for the session and passes it in whenever a new question may be dealt.
 */
data class Drill(
    val settings: DrillSettings,
    val startedAt: Long,
    val phase: Phase,
    val attempts: List<Attempt> = emptyList(),
    val deck: List<Question>,
    /** The index in [deck] of the next question to deal. */
    val deckPos: Int,
    /** The last time an answer was typed, or the start: what the idle limit counts from. */
    val lastInputAt: Long,
    val pausedAt: Long? = null,
    val pausedMillis: Long = 0,
) {
    val score: Int get() = runningScore(attempts, settings.wrongPenalty)
    val right: Int get() = attempts.count { it.outcome == Outcome.RIGHT }
    val wrong: Int get() = attempts.count { it.outcome == Outcome.WRONG }
    val timeouts: Int get() = attempts.count { it.outcome == Outcome.TIMEOUT }
    val isFinished: Boolean get() = phase is Phase.Finished
    val isPaused: Boolean get() = pausedAt != null

    /** When the session's own time limit ends it, pauses added on. */
    val endsAt: Long? get() = settings.sessionMinutes?.let { startedAt + it * 60_000L + pausedMillis }

    /** Time spent drilling so far, pauses left out. */
    fun activeMillis(now: Long): Long {
        val end = (phase as? Phase.Finished)?.at ?: pausedAt ?: now
        return (end - startedAt - pausedMillis).coerceAtLeast(0)
    }

    /** Checks an answer typed at [now]. Does nothing while paused, locked or finished. */
    fun answer(value: Int, now: Long, random: Random): Drill {
        val current = tick(now, random)
        if (current.isPaused) return current
        val asking = current.phase as? Phase.Asking ?: return current
        val outcome = if (value == asking.question.answer) Outcome.RIGHT else Outcome.WRONG
        return current.copy(lastInputAt = now)
            .afterAttempt(Attempt(asking.question, value, outcome, asking.askedAt, now), now, random)
    }

    /**
     * Moves the session on to [now]: times out an unanswered question, unlocks the keypad
     * after a wrong answer's wait, and ends the session on its time or idle limit.
     */
    fun tick(now: Long, random: Random): Drill {
        var d = this
        // Each pass handles the earliest thing due, so a late tick catches up in order.
        repeat(10_000) {
            d = d.stepOnce(now, random) ?: return d
        }
        return d
    }

    private fun stepOnce(now: Long, random: Random): Drill? {
        if (isFinished || isPaused) return null
        val idleAt = settings.idleStopMinutes?.let { lastInputAt + it * 60_000L }
        val endAt = endsAt
        val dueAt = when (val p = phase) {
            is Phase.Asking -> p.deadline
            is Phase.Waiting -> p.until
            is Phase.Finished -> null
        }
        val first = listOfNotNull(idleAt, endAt, dueAt).filter { it <= now }.minOrNull() ?: return null
        return when (first) {
            idleAt -> endIdle()
            endAt -> copy(phase = Phase.Finished(EndReason.TIME_UP, first))
            else -> when (val p = phase) {
                is Phase.Asking ->
                    afterAttempt(Attempt(p.question, null, Outcome.TIMEOUT, p.askedAt, first), first, random)
                is Phase.Waiting -> copy(phase = ask(p.next, first))
                is Phase.Finished -> null
            }
        }
    }

    /** Ends with the stop button. */
    fun stop(now: Long): Drill =
        if (isFinished) this else copy(phase = Phase.Finished(EndReason.STOPPED, pausedAt ?: now))

    fun pause(now: Long): Drill = if (isFinished || isPaused) this else copy(pausedAt = now)

    /** Picks up where the pause left off: every clock that was running moves on by the length of the pause. */
    fun resume(now: Long): Drill {
        val at = pausedAt ?: return this
        val gap = (now - at).coerceAtLeast(0)
        val shifted = when (val p = phase) {
            is Phase.Asking -> p.copy(askedAt = p.askedAt + gap, deadline = p.deadline?.plus(gap))
            is Phase.Waiting -> p.copy(until = p.until + gap)
            is Phase.Finished -> p
        }
        return copy(
            phase = shifted,
            pausedAt = null,
            pausedMillis = pausedMillis + gap,
            lastInputAt = lastInputAt + gap,
        )
    }

    /**
     * Nobody is playing any more. Questions that timed out after the last answer are not
     * hers to count, so they come off and the session ends where she stopped.
     */
    private fun endIdle(): Drill {
        val kept = attempts.dropLastWhile { it.outcome == Outcome.TIMEOUT && it.answeredAt > lastInputAt }
        return copy(attempts = kept, phase = Phase.Finished(EndReason.IDLE, lastInputAt))
    }

    private fun afterAttempt(attempt: Attempt, now: Long, random: Random): Drill {
        val withAttempt = copy(attempts = attempts + attempt)
        val max = settings.maxScore
        if (max != null && withAttempt.score >= max) {
            return withAttempt.copy(phase = Phase.Finished(EndReason.MAX_SCORE, now))
        }
        val (nextQuestion, dealt) = withAttempt.deal(random, attempt.question)
        val waitMs = settings.wrongWaitSeconds * 1000L
        val phase = if (attempt.outcome != Outcome.RIGHT && waitMs > 0) {
            Phase.Waiting(attempt, now + waitMs, nextQuestion)
        } else {
            ask(nextQuestion, now)
        }
        return dealt.copy(phase = phase)
    }

    private fun ask(question: Question, now: Long): Phase.Asking =
        Phase.Asking(question, now, settings.answerSeconds?.let { now + it * 1000L })

    /** The next question, reshuffling the deck when it runs out. */
    private fun deal(random: Random, previous: Question): Pair<Question, Drill> {
        if (deckPos < deck.size) return deck[deckPos] to copy(deckPos = deckPos + 1)
        val fresh = QuestionDeck.shuffled(settings, random, after = previous)
        return fresh[0] to copy(deck = fresh, deckPos = 1)
    }

    companion object {
        fun start(settings: DrillSettings, now: Long, random: Random): Drill {
            val s = settings.normalized()
            val deck = QuestionDeck.shuffled(s, random)
            return Drill(
                settings = s,
                startedAt = now,
                phase = Phase.Asking(deck[0], now, s.answerSeconds?.let { now + it * 1000L }),
                deck = deck,
                deckPos = 1,
                lastInputAt = now,
            )
        }
    }
}
