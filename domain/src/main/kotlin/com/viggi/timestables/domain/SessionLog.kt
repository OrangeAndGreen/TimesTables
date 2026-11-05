package com.viggi.timestables.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * A finished session as it is kept in the log, every attempt included. That costs a few
 * kilobytes a drive and later answers "which facts does she keep missing?".
 */
data class SessionRecord(
    val id: String,
    val settings: DrillSettings,
    val startedAt: Long,
    val endedAt: Long,
    val reason: EndReason,
    val attempts: List<Attempt>,
    val pausedMillis: Long = 0,
) {
    val score: Int get() = runningScore(attempts, settings.wrongPenalty)
    val right: Int get() = attempts.count { it.outcome == Outcome.RIGHT }
    val wrong: Int get() = attempts.count { it.outcome == Outcome.WRONG }
    val timeouts: Int get() = attempts.count { it.outcome == Outcome.TIMEOUT }
    val activeMillis: Long get() = (endedAt - startedAt - pausedMillis).coerceAtLeast(0)

    /** Average time for a right answer, or null with none. */
    val averageRightMillis: Long?
        get() = attempts.filter { it.outcome == Outcome.RIGHT }.takeIf { it.isNotEmpty() }
            ?.map { it.millis }?.average()?.toLong()

    companion object {
        /** The log entry for a finished drill, or null when it isn't finished or nothing was answered. */
        fun of(drill: Drill, id: String): SessionRecord? {
            val end = drill.phase as? Phase.Finished ?: return null
            if (drill.attempts.none { it.given != null }) return null
            return SessionRecord(id, drill.settings, drill.startedAt, end.at, end.reason, drill.attempts, drill.pausedMillis)
        }
    }
}

/** A fact and how it has gone across sessions. */
data class FactStats(val a: Int, val b: Int, val asked: Int, val missed: Int)

/** Facts missed at least once, the most-missed first. 3 × 4 and 4 × 3 count together, smaller number first. */
fun missedFacts(sessions: List<SessionRecord>): List<FactStats> =
    sessions.flatMap { it.attempts }
        .groupBy { minOf(it.question.a, it.question.b) to maxOf(it.question.a, it.question.b) }
        .map { (fact, list) -> FactStats(fact.first, fact.second, list.size, list.count { it.outcome != Outcome.RIGHT }) }
        .filter { it.missed > 0 }
        .sortedWith(compareByDescending<FactStats> { it.missed.toDouble() / it.asked }.thenByDescending { it.missed })

/**
 * The log is a text file with one JSON object per session per line, so a damaged line
 * costs that session alone and the file can be read or copied off the phone by hand.
 */
object SessionCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: SessionRecord): String = buildJsonObject {
        put("v", 1)
        put("id", record.id)
        put("startedAt", record.startedAt)
        put("endedAt", record.endedAt)
        put("pausedMillis", record.pausedMillis)
        put("reason", record.reason.name)
        put("settings", encodeSettings(record.settings))
        put("attempts", buildJsonArray {
            for (a in record.attempts) add(buildJsonObject {
                put("a", a.question.a)
                put("b", a.question.b)
                put("given", a.given?.let { JsonPrimitive(it) } ?: JsonNull)
                put("outcome", a.outcome.name)
                put("askedAt", a.askedAt)
                put("answeredAt", a.answeredAt)
            })
        })
    }.toString()

    /** The session on [line], or null for a blank or unreadable line. */
    fun decode(line: String): SessionRecord? = runCatching {
        if (line.isBlank()) return null
        val o = json.parseToJsonElement(line).jsonObject
        SessionRecord(
            id = o.str("id")!!,
            settings = decodeSettings(o["settings"]!!.jsonObject),
            startedAt = o["startedAt"]!!.jsonPrimitive.long,
            endedAt = o["endedAt"]!!.jsonPrimitive.long,
            reason = EndReason.valueOf(o.str("reason")!!),
            attempts = o["attempts"]!!.jsonArray.map { e ->
                val a = e.jsonObject
                Attempt(
                    question = Question(a["a"]!!.jsonPrimitive.int, a["b"]!!.jsonPrimitive.int),
                    given = a["given"]?.jsonPrimitive?.intOrNull,
                    outcome = Outcome.valueOf(a.str("outcome")!!),
                    askedAt = a["askedAt"]!!.jsonPrimitive.long,
                    answeredAt = a["answeredAt"]!!.jsonPrimitive.long,
                )
            },
            pausedMillis = o["pausedMillis"]?.jsonPrimitive?.long ?: 0,
        )
    }.getOrNull()

    fun encodeSettings(s: DrillSettings): JsonObject = buildJsonObject {
        put("tablesFrom", s.tablesFrom)
        put("tablesTo", s.tablesTo)
        put("timesFrom", s.timesFrom)
        put("timesTo", s.timesTo)
        put("maxScore", s.maxScore.orNull())
        put("answerSeconds", s.answerSeconds.orNull())
        put("wrongWaitSeconds", s.wrongWaitSeconds)
        put("wrongPenalty", s.wrongPenalty)
        put("sessionMinutes", s.sessionMinutes.orNull())
        put("idleStopMinutes", s.idleStopMinutes.orNull())
    }

    /** Settings from [o]; anything missing keeps its default, so older entries still read. */
    fun decodeSettings(o: JsonObject): DrillSettings {
        val d = DrillSettings()
        fun int(key: String, default: Int) = o[key]?.jsonPrimitive?.intOrNull ?: default
        fun optional(key: String, default: Int?) =
            if (key in o) o[key]?.jsonPrimitive?.intOrNull else default
        return DrillSettings(
            tablesFrom = int("tablesFrom", d.tablesFrom),
            tablesTo = int("tablesTo", d.tablesTo),
            timesFrom = int("timesFrom", d.timesFrom),
            timesTo = int("timesTo", d.timesTo),
            maxScore = optional("maxScore", d.maxScore),
            answerSeconds = optional("answerSeconds", d.answerSeconds),
            wrongWaitSeconds = int("wrongWaitSeconds", d.wrongWaitSeconds),
            wrongPenalty = int("wrongPenalty", d.wrongPenalty),
            sessionMinutes = optional("sessionMinutes", d.sessionMinutes),
            idleStopMinutes = optional("idleStopMinutes", d.idleStopMinutes),
        )
    }

    fun encodeSettingsString(s: DrillSettings): String = encodeSettings(s).toString()

    fun decodeSettingsString(text: String?): DrillSettings =
        text?.let { runCatching { decodeSettings(json.parseToJsonElement(it).jsonObject) }.getOrNull() }
            ?: DrillSettings()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun Int?.orNull(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
}
