package com.viggi.timestables.domain

import kotlin.random.Random

/** Two numbers to multiply, in the order they are shown. */
data class Question(val a: Int, val b: Int) {
    val answer: Int get() = a * b

    /** 3 × 4 and 4 × 3 are the same fact. */
    fun sameFactAs(other: Question): Boolean =
        (a == other.a && b == other.b) || (a == other.b && b == other.a)

    override fun toString(): String = "$a × $b"
}

/**
 * Deals every question in the settings' ranges once, in a random order, before any comes
 * round again, so a drive covers the whole range instead of whatever a coin keeps picking.
 */
object QuestionDeck {

    /** Every question the settings ask for, table number first. */
    fun all(settings: DrillSettings): List<Question> {
        val s = settings.normalized()
        return (s.tablesFrom..s.tablesTo).flatMap { t -> (s.timesFrom..s.timesTo).map { x -> Question(t, x) } }
    }

    /**
     * A freshly shuffled deck. Each question is shown either way round at random, and the
     * deck never starts with the same fact as [after], the question just asked.
     */
    fun shuffled(settings: DrillSettings, random: Random, after: Question? = null): List<Question> {
        val deck = all(settings).shuffled(random)
            .map { if (random.nextBoolean()) Question(it.b, it.a) else it }
            .toMutableList()
        if (after != null && deck.size > 1 && deck[0].sameFactAs(after)) {
            val swapWith = deck.indexOfFirst { !it.sameFactAs(after) }
            if (swapWith > 0) deck[0] = deck[swapWith].also { deck[swapWith] = deck[0] }
        }
        return deck
    }
}
