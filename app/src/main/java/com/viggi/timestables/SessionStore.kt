package com.viggi.timestables

import android.content.Context
import com.viggi.timestables.domain.DrillSettings
import com.viggi.timestables.domain.SessionCodec
import com.viggi.timestables.domain.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The session log, `sessions.jsonl` in the app's files: one finished session per line,
 * appended at the end of each session. The last settings used are kept in preferences
 * so the next drive starts the same way.
 */
class SessionStore(context: Context) {
    private val file = File(context.filesDir, "sessions.jsonl")
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Every logged session, newest first. */
    suspend fun load(): List<SessionRecord> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        file.readLines().mapNotNull(SessionCodec::decode).sortedByDescending { it.startedAt }
    }

    suspend fun append(record: SessionRecord) = withContext(Dispatchers.IO) {
        file.appendText(SessionCodec.encode(record) + "\n")
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        val kept = file.readLines().filter { line -> line.isNotBlank() && SessionCodec.decode(line)?.id != id }
        val tmp = File(file.parentFile, "sessions.jsonl.tmp")
        tmp.writeText(kept.joinToString("") { it + "\n" })
        tmp.renameTo(file)
    }

    fun loadSettings(): DrillSettings = SessionCodec.decodeSettingsString(prefs.getString("drill", null))

    fun saveSettings(settings: DrillSettings) {
        prefs.edit().putString("drill", SessionCodec.encodeSettingsString(settings)).apply()
    }

    var soundOn: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) = prefs.edit().putBoolean("sound", value).apply()
}
