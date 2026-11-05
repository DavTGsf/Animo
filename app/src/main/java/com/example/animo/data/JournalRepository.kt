package com.example.animo.data

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Guarda el diario completo como un JSON cifrado (AES-GCM, llave en Keystore) en el
 * almacenamiento privado de la app. Los datos de un diario personal caben de sobra en memoria.
 */
class JournalRepository(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "journal.bin"))
    private val box = CryptoBox("animo_journal_key")
    private val mutex = Mutex()

    private val _journal = MutableStateFlow(Journal())
    val journal: StateFlow<Journal> = _journal.asStateFlow()

    suspend fun load() = mutex.withLock {
        _journal.value = withContext(Dispatchers.IO) {
            if (!file.baseFile.exists()) return@withContext Journal()
            try {
                JournalJson.decode(String(box.decrypt(file.readFully()), Charsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo leer el diario", e)
                Journal()
            }
        }
    }

    suspend fun upsert(entry: DayEntry) = update { it.copy(entries = it.entries + (entry.date to entry)) }

    suspend fun delete(date: LocalDate) = update { it.copy(entries = it.entries - date) }

    suspend fun addCustomActivity(name: String) = update { j ->
        if (j.allActivities.any { it.equals(name, ignoreCase = true) }) j
        else j.copy(customActivities = j.customActivities + name)
    }

    suspend fun removeCustomActivity(name: String) =
        update { it.copy(customActivities = it.customActivities - name) }

    suspend fun wipe() = update { Journal() }

    suspend fun replaceAll(journal: Journal) = update { journal }

    private suspend fun update(transform: (Journal) -> Journal) = mutex.withLock {
        val next = transform(_journal.value)
        if (next == _journal.value) return@withLock
        _journal.value = next
        withContext(Dispatchers.IO) { persist(next) }
    }

    private fun persist(journal: Journal) {
        val bytes = box.encrypt(JournalJson.encode(journal).toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private companion object {
        const val TAG = "JournalRepository"
    }
}

internal object JournalJson {
    fun encode(journal: Journal): String = JSONObject().apply {
        put("version", 1)
        put("customActivities", JSONArray(journal.customActivities))
        put("entries", JSONArray().apply {
            journal.entries.values.sortedBy { it.date }.forEach { e ->
                put(JSONObject().apply {
                    put("date", e.date.toString())
                    put("mood", e.mood.name)
                    put("energy", e.energy)
                    put("activities", JSONArray(e.activities))
                    put("reflection", e.reflection)
                    put("createdAt", e.createdAt)
                    put("updatedAt", e.updatedAt)
                })
            }
        })
    }.toString()

    fun decode(text: String): Journal {
        val root = JSONObject(text)
        val custom = root.optJSONArray("customActivities").strings()
        val entries = root.optJSONArray("entries") ?: JSONArray()
        val map = buildMap {
            for (i in 0 until entries.length()) {
                val o = entries.getJSONObject(i)
                val mood = Mood.fromName(o.getString("mood")) ?: continue
                val date = LocalDate.parse(o.getString("date"))
                put(
                    date,
                    DayEntry(
                        date = date,
                        mood = mood,
                        energy = o.getInt("energy").coerceIn(MIN_ENERGY, MAX_ENERGY),
                        activities = o.optJSONArray("activities").strings(),
                        reflection = o.optString("reflection"),
                        createdAt = o.getLong("createdAt"),
                        updatedAt = o.optLong("updatedAt", o.getLong("createdAt")),
                    )
                )
            }
        }
        return Journal(entries = map, customActivities = custom)
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { getString(it) }
}
