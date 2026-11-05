package com.example.animo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.animo.data.AppSettings
import com.example.animo.data.DayEntry
import com.example.animo.data.JournalRepository
import com.example.animo.data.MAX_ACTIVITY_NAME
import com.example.animo.data.MAX_ENERGY
import com.example.animo.data.MIN_ENERGY
import com.example.animo.data.Mood
import com.example.animo.data.SettingsStore
import com.example.animo.security.Biometrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.example.animo.domain.SyntheticData

enum class LockState { Setup, Locked, Unlocked }

/** Borrador del registro que se está creando o editando. Vive en el ViewModel para sobrevivir al bloqueo. */
data class EditorDraft(
    val date: LocalDate,
    val mood: Mood?,
    val energy: Int,
    val activities: Set<String>,
    val reflection: String,
    val existing: DayEntry?,
    val openedAt: Long,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JournalRepository(app)
    private val store = SettingsStore(app)

    val journal = repo.journal
    val settings: StateFlow<AppSettings> = store.settings

    private val _lockState = MutableStateFlow(if (settings.value.hasPin) LockState.Locked else LockState.Setup)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _draft = MutableStateFlow<EditorDraft?>(null)
    val draft: StateFlow<EditorDraft?> = _draft.asStateFlow()

    /** Se incrementa cada vez que se guarda un registro (dispara la animación de celebración). */
    private val _savedTick = MutableStateFlow(0)
    val savedTick: StateFlow<Int> = _savedTick.asStateFlow()

    private val _offerBiometric = MutableStateFlow(false)
    val offerBiometric: StateFlow<Boolean> = _offerBiometric.asStateFlow()

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private var lastInteraction = System.currentTimeMillis()
    private var backgroundedAt: Long? = null

    init {
        viewModelScope.launch { repo.load() }
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                _today.value = LocalDate.now()
                checkInactivity(System.currentTimeMillis())
            }
        }
    }

    // ---------------- Bloqueo ----------------

    val biometricAvailable: Boolean get() = Biometrics.isAvailable(getApplication())

    fun submitPin(pin: String): SettingsStore.PinResult {
        val result = store.checkPin(pin)
        if (result is SettingsStore.PinResult.Ok) unlock()
        return result
    }

    fun verifyPin(pin: String): SettingsStore.PinResult = store.checkPin(pin)

    fun lockoutUntil(): Long = store.lockoutUntil()

    fun createPin(pin: String) {
        val firstTime = !settings.value.hasPin
        store.savePin(pin)
        unlock()
        if (firstTime && biometricAvailable) _offerBiometric.value = true
    }

    fun changePin(newPin: String) = store.savePin(newPin)

    fun unlockWithBiometric() = unlock()

    fun dismissBiometricOffer() {
        _offerBiometric.value = false
    }

    fun lockNow() {
        if (settings.value.hasPin) _lockState.value = LockState.Locked
    }

    fun loadDemoData() {
        viewModelScope.launch { repo.replaceAll(SyntheticData.generate(_today.value)) }
    }

    private fun unlock() {
        lastInteraction = System.currentTimeMillis()
        _lockState.value = LockState.Unlocked
    }

    /** Cualquier toque o tecla reinicia el contador de inactividad. */
    fun touch() {
        lastInteraction = System.currentTimeMillis()
    }

    fun onAppBackground(now: Long = System.currentTimeMillis()) {
        backgroundedAt = now
    }

    fun onAppForeground(now: Long = System.currentTimeMillis()) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (_lockState.value == LockState.Unlocked && now - since >= settings.value.autoLockSeconds * 1000L) {
            lockNow()
        }
        lastInteraction = now
    }

    private fun checkInactivity(now: Long) {
        if (_lockState.value != LockState.Unlocked || backgroundedAt != null) return
        // "Al salir" no bloquea mientras se usa la app; aun así, 1 minuto sin tocar nada bloquea.
        val limit = settings.value.autoLockSeconds.coerceAtLeast(60) * 1000L
        if (now - lastInteraction >= limit) lockNow()
    }

    // ---------------- Editor ----------------

    fun canCreate(date: LocalDate): Boolean {
        val today = _today.value
        return date == today || (date.isBefore(today) && settings.value.allowPastDays)
    }

    fun openEditor(date: LocalDate, mood: Mood? = null): Boolean {
        val existing = journal.value.entries[date]
        if (date.isAfter(_today.value) || (existing == null && !canCreate(date))) return false
        _draft.value = EditorDraft(
            date = date,
            mood = mood ?: existing?.mood,
            energy = existing?.energy ?: 5,
            activities = existing?.activities?.toSet().orEmpty(),
            reflection = existing?.reflection.orEmpty(),
            existing = existing,
            openedAt = System.currentTimeMillis(),
        )
        return true
    }

    fun closeEditor() {
        _draft.value = null
    }

    fun setMood(mood: Mood) = _draft.update { it?.copy(mood = mood) }

    fun setEnergy(energy: Int) = _draft.update { it?.copy(energy = energy.coerceIn(MIN_ENERGY, MAX_ENERGY)) }

    fun toggleActivity(name: String) = _draft.update { d ->
        d?.copy(activities = if (name in d.activities) d.activities - name else d.activities + name)
    }

    fun setReflection(text: String) {
        touch()
        _draft.update { it?.copy(reflection = text.take(settings.value.maxReflectionChars)) }
    }

    fun saveDraft() {
        val d = _draft.value ?: return
        val mood = d.mood ?: return
        val now = System.currentTimeMillis()
        val order = journal.value.allActivities
        val entry = DayEntry(
            date = d.date,
            mood = mood,
            energy = d.energy,
            activities = d.activities.sortedBy { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            reflection = d.reflection.trim().take(settings.value.maxReflectionChars),
            createdAt = d.existing?.createdAt ?: now,
            updatedAt = now,
        )
        _draft.value = null
        viewModelScope.launch {
            repo.upsert(entry)
            _savedTick.value++
        }
    }

    fun deleteEntry(date: LocalDate) {
        viewModelScope.launch { repo.delete(date) }
    }

    /** Devuelve un mensaje de error o null si se agregó. */
    fun addCustomActivity(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        when {
            name.isEmpty() -> return "Escribe un nombre"
            name.length > MAX_ACTIVITY_NAME -> return "Máximo $MAX_ACTIVITY_NAME caracteres"
            journal.value.allActivities.any { it.equals(name, ignoreCase = true) } -> return "Esa actividad ya existe"
        }
        viewModelScope.launch { repo.addCustomActivity(name) }
        _draft.update { it?.copy(activities = it.activities + name) }
        return null
    }

    fun removeCustomActivity(name: String) {
        viewModelScope.launch { repo.removeCustomActivity(name) }
    }

    // ---------------- Ajustes ----------------

    fun setBiometric(enabled: Boolean) {
        store.setBiometric(enabled)
        _offerBiometric.value = false
    }

    fun setAutoLock(seconds: Int) = store.setAutoLock(seconds)
    fun setMaxChars(chars: Int) = store.setMaxChars(chars)
    fun setAllowPastDays(allow: Boolean) = store.setAllowPastDays(allow)
    fun setSecureScreen(secure: Boolean) = store.setSecureScreen(secure)

    fun wipeJournal() {
        viewModelScope.launch { repo.wipe() }
    }
}
