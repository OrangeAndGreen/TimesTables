package com.viggi.timestables

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.viggi.timestables.domain.Tones

enum class Sound { RIGHT, WRONG, TIMEOUT, FANFARE }

/**
 * Plays the drill's sounds on the media stream, so they follow the media volume and come
 * out of the car speakers when the phone is on the car's Bluetooth. Each sound is loaded
 * once into its own track, so it plays without delay.
 */
class SoundPlayer {
    private val tracks: Map<Sound, AudioTrack> = mapOf(
        Sound.RIGHT to Tones.right(),
        Sound.WRONG to Tones.wrong(),
        Sound.TIMEOUT to Tones.timeout(),
        Sound.FANFARE to Tones.fanfare(),
    ).mapValues { (_, samples) -> load(samples) }

    fun play(sound: Sound) {
        // Cut off whatever is still sounding, so a quick answer gets its own sound at once.
        tracks.values.forEach { runCatching { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop() } }
        val track = tracks.getValue(sound)
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() = tracks.values.forEach { it.release() }

    private fun load(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Tones.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }
}
