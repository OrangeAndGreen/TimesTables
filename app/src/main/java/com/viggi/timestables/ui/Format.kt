package com.viggi.timestables.ui

import com.viggi.timestables.domain.DrillSettings
import com.viggi.timestables.domain.EndReason

/** 65 000 ms as "1:05", an hour or more as "1:02:03". */
fun formatClock(millis: Long): String {
    val total = (millis.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Seconds with one decimal, for answer times: 2 340 ms as "2.3 s". */
fun formatSeconds(millis: Long): String = "%.1f s".format(millis / 1000.0)

/** "7 × 1–12", "2–10 × 1–10": the range of questions in a session. */
fun rangeText(s: DrillSettings): String {
    fun range(from: Int, to: Int) = if (from == to) "$from" else "$from–$to"
    return "${range(s.tablesFrom, s.tablesTo)} × ${range(s.timesFrom, s.timesTo)}"
}

/** The limits that were on, in a few words: "to 20 · 5 s each · wait 2 s · −1 wrong". */
fun limitsText(s: DrillSettings): String = listOfNotNull(
    s.maxScore?.let { "to $it" },
    s.sessionMinutes?.let { "$it min" },
    s.answerSeconds?.let { "$it s each" },
    s.wrongWaitSeconds.takeIf { it > 0 }?.let { "wait $it s" },
    s.wrongPenalty.takeIf { it > 0 }?.let { "−$it wrong" },
).joinToString(" · ")

fun reasonText(reason: EndReason): String = when (reason) {
    EndReason.MAX_SCORE -> "Reached the top score"
    EndReason.TIME_UP -> "Time's up"
    EndReason.STOPPED -> "Stopped"
    EndReason.IDLE -> "Stopped answering"
}
