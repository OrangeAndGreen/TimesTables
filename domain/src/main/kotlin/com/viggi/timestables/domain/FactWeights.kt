package com.viggi.timestables.domain

import kotlin.math.pow
import kotlin.random.Random

/**
 * How often each fact comes up when "practice the ones missed" is on: a weight of 1 is
 * once a pass through the deck, as without it, and 2.5 is two or three times. Facts are
 * keyed as [missedFacts] keys them, by operation and fact, so 3 × 4 and 4 × 3 share a
 * weight, and so do 12 ÷ 3 and 12 ÷ 4, but the divisions apart from the multiplication.
 * A fact that isn't in the map, such as one never asked, keeps [BASE_WEIGHT].
 */
data class FactWeights(private val weights: Map<Pair<Op, Pair<Int, Int>>, Double> = emptyMap()) {

    fun of(q: Question): Double = weights[q.op to q.fact] ?: BASE_WEIGHT

    /** How many of [questions] come up more often than the rest. */
    fun boosted(questions: List<Question>): Int = questions.count { of(it) > BASE_WEIGHT }

    /**
     * How many copies of a question with weight [w] go into one pass of the deck: the
     * whole part, and one more with the chance of the fraction, so it averages [w]. A
     * whole weight draws nothing from [random], so a deck with no history deals exactly
     * as the plain deck does from the same seed.
     */
    fun copies(q: Question, random: Random): Int {
        val w = of(q)
        val whole = w.toInt()
        val fraction = w - whole
        return whole + if (fraction > 0 && random.nextDouble() < fraction) 1 else 0
    }

    companion object {
        /** Every fact once a pass: the deck without practice. */
        val NONE = FactWeights()

        /** A fact never asked, or never missed, comes up once a pass, as it always has. */
        const val BASE_WEIGHT = 1.0

        /**
         * How far a miss rate lifts the weight: a fact missed every time recently comes up
         * about four times a pass. Enough to hear it again two or three times on one
         * drive, not so much that the rest of the range disappears.
         */
        const val MISS_BOOST = 3.0

        /**
         * The most a fact can weigh. With [MISS_BOOST] at 3 the weight can only reach 4
         * anyway; this keeps a later change to the boost from letting one fact crowd out
         * the deck.
         */
        const val MAX_WEIGHT = 4.0

        /**
         * Imaginary right answers added to every fact's record, so one unlucky miss on a
         * fact asked once reads as a third, not as "always wrong" (weight 2, not 4).
         */
        const val PRIOR_RIGHT = 2.0

        /**
         * A session's answers count half as much five sessions later. About a week of
         * school runs, so a fact she has since learned stops being pushed within a week
         * or two, while one she keeps missing stays up.
         */
        const val HALF_LIFE_SESSIONS = 5.0

        /**
         * Weights from the log. Each answer counts by how recent its session is, newest
         * first; a miss is anything but a right answer, as in [missedFacts]. The weight is
         * 1 + [MISS_BOOST] × the recent miss rate, capped at [MAX_WEIGHT], so it is never
         * below 1 and no fact drops out of the deck.
         */
        fun from(sessions: List<SessionRecord>): FactWeights {
            val asked = HashMap<Pair<Op, Pair<Int, Int>>, Double>()
            val missed = HashMap<Pair<Op, Pair<Int, Int>>, Double>()
            sessions.sortedByDescending { it.startedAt }.forEachIndexed { age, session ->
                val weight = 0.5.pow(age / HALF_LIFE_SESSIONS)
                for (a in session.attempts) {
                    val key = a.question.op to a.question.fact
                    asked.merge(key, weight, Double::plus)
                    if (a.outcome != Outcome.RIGHT) missed.merge(key, weight, Double::plus)
                }
            }
            val weights = missed.mapValues { (key, m) ->
                val rate = m / (asked.getValue(key) + PRIOR_RIGHT)
                (BASE_WEIGHT + MISS_BOOST * rate).coerceIn(BASE_WEIGHT, MAX_WEIGHT)
            }
            return FactWeights(weights)
        }
    }
}
