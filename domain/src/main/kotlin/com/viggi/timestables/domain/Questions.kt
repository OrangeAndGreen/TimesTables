package com.viggi.timestables.domain

import kotlin.random.Random

enum class Op(val symbol: String) { TIMES("×"), DIVIDE("÷") }

/**
 * A question in the order it is shown: [a] × [b], or [a] ÷ [b] for a division, which is
 * always whole, since it is a times-table fact turned round (21 ÷ 7 = 3 from 7 × 3).
 */
data class Question(val a: Int, val b: Int, val op: Op = Op.TIMES) {
    val answer: Int
        get() = when (op) {
            Op.TIMES -> a * b
            Op.DIVIDE -> if (b == 0) 0 else a / b
        }

    /** The times-table fact behind the question, smaller number first: 3 × 4, 4 × 3, 12 ÷ 3 and 12 ÷ 4 are all 3 × 4. */
    val fact: Pair<Int, Int>
        get() = when (op) {
            Op.TIMES -> minOf(a, b) to maxOf(a, b)
            Op.DIVIDE -> minOf(b, answer) to maxOf(b, answer)
        }

    /** Questions from the same fact, either operation; one right after the other gives the answer away. */
    fun sameFactAs(other: Question): Boolean = fact == other.fact

    override fun toString(): String = "$a ${op.symbol} $b"

    companion object {
        /** [t] × [x] turned into a division by [divisor], one of the two; null when that would divide by zero. */
        fun division(t: Int, x: Int, divisor: Int): Question? =
            if (divisor == 0) null else Question(t * x, divisor, Op.DIVIDE)
    }
}

/**
 * Deals every question in the settings' ranges once, in a random order, before any comes
 * round again, so a drive covers the whole range instead of whatever a coin keeps picking.
 */
object QuestionDeck {

    /**
     * Every question the settings ask for, table number first: t × x, and for division
     * t·x ÷ t (or ÷ x when t is 0). With both, each fact is asked both ways. A division
     * deck with nothing to divide by (every number 0) falls back to multiplying.
     */
    fun all(settings: DrillSettings): List<Question> {
        val s = settings.normalized()
        val facts = (s.tablesFrom..s.tablesTo).flatMap { t -> (s.timesFrom..s.timesTo).map { x -> t to x } }
        val times = facts.map { (t, x) -> Question(t, x) }
        val divides = facts.mapNotNull { (t, x) -> Question.division(t, x, if (t != 0) t else x) }
        return when (s.operations) {
            Operations.MULTIPLY -> times
            Operations.DIVIDE -> divides.ifEmpty { times }
            Operations.BOTH -> times + divides
        }
    }

    /**
     * A freshly shuffled deck. Each question is turned round at random (4 × 3 or 3 × 4,
     * 12 ÷ 3 or 12 ÷ 4), and no question follows one from the same fact, [after] (the
     * question just asked) included, wherever the deck leaves a way to avoid it.
     */
    fun shuffled(settings: DrillSettings, random: Random, after: Question? = null): List<Question> {
        val pool = all(settings).shuffled(random).map { flip(it, random) }.toMutableList()
        val deck = mutableListOf<Question>()
        fun clash(q: Question, previous: Question?) = previous != null && q.sameFactAs(previous)
        while (pool.isNotEmpty()) {
            val last = deck.lastOrNull() ?: after
            val next = pool.indexOfFirst { !clash(it, last) }
            if (next >= 0) {
                deck += pool.removeAt(next)
                continue
            }
            // Everything left clashes with the last one dealt: put one in an earlier gap that fits.
            val q = pool.removeAt(0)
            val gap = deck.indices.firstOrNull { i ->
                !clash(q, if (i == 0) after else deck[i - 1]) && !clash(q, deck[i])
            }
            if (gap != null) deck.add(gap, q) else deck += q
        }
        return deck
    }

    private fun flip(q: Question, random: Random): Question {
        if (!random.nextBoolean()) return q
        return when (q.op) {
            Op.TIMES -> Question(q.b, q.a)
            Op.DIVIDE -> Question.division(q.b, q.answer, divisor = q.answer) ?: q
        }
    }
}
