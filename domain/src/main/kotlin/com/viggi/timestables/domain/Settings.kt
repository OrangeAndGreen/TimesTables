package com.viggi.timestables.domain

/**
 * What a session drills and how strict it is, chosen on the setup screen before it starts.
 *
 * A question multiplies a number from the tables range by one from the times range, so
 * "tables 7 to 7, times 1 to 12" drills the seven times table, and "2 to 10 by 2 to 10"
 * mixes them all. A null limit is off.
 */
data class DrillSettings(
    val tablesFrom: Int = 2,
    val tablesTo: Int = 10,
    val timesFrom: Int = 1,
    val timesTo: Int = 10,
    /** Reaching this score ends the session with the congratulations screen. */
    val maxScore: Int? = 20,
    /** Seconds allowed for each answer before it counts as wrong. */
    val answerSeconds: Int? = null,
    /** Seconds the keypad stays locked after a wrong answer, showing the right one. */
    val wrongWaitSeconds: Int = 0,
    /** Points taken off for a wrong answer. The score never goes below zero. */
    val wrongPenalty: Int = 0,
    /** The session ends by itself after this long, such as the length of the drive. */
    val sessionMinutes: Int? = null,
    /** With no answer typed for this long the session ends, at the last answer. */
    val idleStopMinutes: Int? = 3,
) {
    /** The same settings with each range the right way round and nothing negative. */
    fun normalized(): DrillSettings = copy(
        tablesFrom = minOf(tablesFrom, tablesTo).coerceAtLeast(0),
        tablesTo = maxOf(tablesFrom, tablesTo).coerceAtLeast(0),
        timesFrom = minOf(timesFrom, timesTo).coerceAtLeast(0),
        timesTo = maxOf(timesFrom, timesTo).coerceAtLeast(0),
        maxScore = maxScore?.takeIf { it > 0 },
        answerSeconds = answerSeconds?.takeIf { it > 0 },
        wrongWaitSeconds = wrongWaitSeconds.coerceAtLeast(0),
        wrongPenalty = wrongPenalty.coerceAtLeast(0),
        sessionMinutes = sessionMinutes?.takeIf { it > 0 },
        idleStopMinutes = idleStopMinutes?.takeIf { it > 0 },
    )
}
