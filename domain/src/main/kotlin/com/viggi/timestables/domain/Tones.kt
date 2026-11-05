package com.viggi.timestables.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The sounds, made in code as 16-bit mono samples. Each one is short and easy to tell
 * apart from the driver's seat without looking: a bright rising chime for right, a low
 * falling "uh-oh" for wrong, a flat double buzz for time running out, and a fanfare for
 * reaching the maximum score.
 */
object Tones {
    const val SAMPLE_RATE = 44_100

    private data class Note(val hz: Double, val ms: Int, val bright: Boolean)

    fun right(): ShortArray = render(listOf(Note(880.0, 90, true), Note(1318.5, 190, true)))

    fun wrong(): ShortArray = render(listOf(Note(311.1, 180, false), Note(233.1, 320, false)))

    fun timeout(): ShortArray =
        render(listOf(Note(196.0, 140, false), Note(0.0, 70, false), Note(196.0, 140, false)))

    fun fanfare(): ShortArray = render(
        listOf(
            Note(523.3, 120, true), Note(659.3, 120, true), Note(784.0, 120, true),
            Note(0.0, 40, true), Note(784.0, 100, true), Note(1046.5, 520, true),
        )
    )

    /** Notes one after another; a note at 0 Hz is a rest. */
    private fun render(notes: List<Note>): ShortArray {
        val out = ShortArray(notes.sumOf { it.ms * SAMPLE_RATE / 1000 })
        var pos = 0
        for (n in notes) {
            val count = n.ms * SAMPLE_RATE / 1000
            if (n.hz > 0) for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                val x = 2 * PI * n.hz * t
                // A bright note adds the octave and twelfth; a dull one adds odd harmonics for a buzzier edge.
                val wave = if (n.bright) {
                    sin(x) + 0.35 * sin(2 * x) + 0.15 * sin(3 * x)
                } else {
                    sin(x) + 0.4 * sin(3 * x) + 0.2 * sin(5 * x)
                }
                out[pos + i] = (wave / 1.6 * envelope(i, count) * 0.8 * Short.MAX_VALUE).roundToInt()
                    .coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            pos += count
        }
        return out
    }

    /** A quick attack and a gentle fall, so notes don't click at their edges. */
    private fun envelope(i: Int, count: Int): Double {
        val attack = (SAMPLE_RATE * 0.006).toInt()
        val release = (SAMPLE_RATE * 0.03).toInt()
        val rise = if (i < attack) i.toDouble() / attack else 1.0
        val fall = if (i > count - release) (count - i).toDouble() / release else 1.0
        val decay = exp(-1.2 * i / count)
        return rise * fall * decay
    }
}
