package com.viggi.timestables

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viggi.timestables.domain.Drill
import com.viggi.timestables.domain.DrillSettings
import com.viggi.timestables.domain.EndReason
import com.viggi.timestables.domain.Outcome
import com.viggi.timestables.domain.Phase
import com.viggi.timestables.domain.SessionRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

sealed interface Screen {
    data object Setup : Screen
    data object Drilling : Screen
    /** The end of a session; [record] is null when nothing was answered, so nothing was logged. */
    data class Result(val drill: Drill, val record: SessionRecord?) : Screen
    data object History : Screen
    data class Detail(val id: String) : Screen
}

class DrillViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val sounds = SoundPlayer()
    private var random = Random(System.nanoTime())
    private var ticker: Job? = null

    private val _screen = MutableStateFlow<Screen>(Screen.Setup)
    val screen: StateFlow<Screen> = _screen

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<DrillSettings> = _settings

    private val _soundOn = MutableStateFlow(store.soundOn)
    val soundOn: StateFlow<Boolean> = _soundOn

    private val _drill = MutableStateFlow<Drill?>(null)
    val drill: StateFlow<Drill?> = _drill

    /** The digits typed so far for the current question. */
    private val _typed = MutableStateFlow("")
    val typed: StateFlow<String> = _typed

    /** The clock the drill screen draws its countdowns from, moved on by the ticker. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now

    private val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    val sessions: StateFlow<List<SessionRecord>> = _sessions

    init {
        viewModelScope.launch { _sessions.value = store.load() }
    }

    fun updateSettings(settings: DrillSettings) {
        _settings.value = settings
        store.saveSettings(settings)
    }

    fun setSoundOn(on: Boolean) {
        _soundOn.value = on
        store.soundOn = on
    }

    fun start() {
        val now = System.currentTimeMillis()
        random = Random(System.nanoTime())
        store.saveSettings(_settings.value)
        _drill.value = Drill.start(_settings.value, now, random)
        _typed.value = ""
        _now.value = now
        _screen.value = Screen.Drilling
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(100)
                update { it.tick(System.currentTimeMillis(), random) }
            }
        }
    }

    fun typeDigit(digit: Int) {
        val d = _drill.value ?: return
        if (d.phase !is Phase.Asking || d.isPaused) return
        if (_typed.value.length < 4) _typed.value = (_typed.value + digit).trimStart('0').ifEmpty { "0" }
    }

    fun backspace() {
        _typed.value = _typed.value.dropLast(1)
    }

    fun submit() {
        val value = _typed.value.toIntOrNull() ?: return
        update { it.answer(value, System.currentTimeMillis(), random) }
    }

    fun pause() = update { it.pause(System.currentTimeMillis()) }

    fun resume() = update { it.resume(System.currentTimeMillis()) }

    fun stop() = update { it.stop(System.currentTimeMillis()) }

    fun show(screen: Screen) {
        _screen.value = screen
    }

    fun delete(id: String) {
        viewModelScope.launch {
            store.delete(id)
            _sessions.value = store.load()
            _screen.value = Screen.History
        }
    }

    /** Applies a change to the drill and does what follows from it: sounds, clearing the answer, the end. */
    private fun update(change: (Drill) -> Drill) {
        val before = _drill.value ?: return
        if (before.isFinished) return
        val after = change(before)
        _now.value = System.currentTimeMillis()
        if (after == before) return
        _drill.value = after

        val newAttempts = after.attempts.drop(before.attempts.size)
        if (newAttempts.isNotEmpty() || questionOf(before) != questionOf(after)) _typed.value = ""

        val finished = after.phase as? Phase.Finished
        if (_soundOn.value) {
            when {
                finished?.reason == EndReason.MAX_SCORE -> sounds.play(Sound.FANFARE)
                newAttempts.isNotEmpty() -> sounds.play(
                    when (newAttempts.last().outcome) {
                        Outcome.RIGHT -> Sound.RIGHT
                        Outcome.WRONG -> Sound.WRONG
                        Outcome.TIMEOUT -> Sound.TIMEOUT
                    }
                )
            }
        }
        if (finished != null) finish(after)
    }

    private fun finish(drill: Drill) {
        ticker?.cancel()
        ticker = null
        val record = SessionRecord.of(drill, UUID.randomUUID().toString())
        _screen.value = Screen.Result(drill, record)
        if (record != null) viewModelScope.launch {
            store.append(record)
            _sessions.value = store.load()
        }
    }

    private fun questionOf(d: Drill) = (d.phase as? Phase.Asking)?.question

    override fun onCleared() {
        sounds.release()
    }
}
