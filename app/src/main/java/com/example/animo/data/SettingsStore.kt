package com.example.animo.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Preferencias de la app y verificación del PIN. El PIN nunca se guarda: solo
 * HMAC(llave Keystore, sal + PIN). Tras varios intentos fallidos se bloquea temporalmente.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("animo_settings", Context.MODE_PRIVATE)
    private val hmac = KeystoreHmac("animo_pin_key")

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        hasPin = prefs.contains(KEY_PIN_HASH),
        biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
        autoLockSeconds = prefs.getInt(KEY_AUTO_LOCK, 60),
        maxReflectionChars = prefs.getInt(KEY_MAX_CHARS, 500),
        allowPastDays = prefs.getBoolean(KEY_ALLOW_PAST, false),
        secureScreen = prefs.getBoolean(KEY_SECURE, true),
    )

    private fun change(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        _settings.value = read()
    }

    fun setBiometric(enabled: Boolean) = change { putBoolean(KEY_BIOMETRIC, enabled) }
    fun setAutoLock(seconds: Int) = change { putInt(KEY_AUTO_LOCK, seconds) }
    fun setMaxChars(chars: Int) = change { putInt(KEY_MAX_CHARS, chars) }
    fun setAllowPastDays(allow: Boolean) = change { putBoolean(KEY_ALLOW_PAST, allow) }
    fun setSecureScreen(secure: Boolean) = change { putBoolean(KEY_SECURE, secure) }

    // ---- PIN ----

    fun savePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        change {
            putString(KEY_PIN_SALT, salt.b64())
            putString(KEY_PIN_HASH, hash(pin, salt).b64())
            remove(KEY_FAILED)
            remove(KEY_LOCKOUT_UNTIL)
        }
    }

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class LockedOut(val untilMillis: Long) : PinResult
    }

    fun lockoutUntil(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

    fun checkPin(pin: String, now: Long = System.currentTimeMillis()): PinResult {
        val until = lockoutUntil()
        if (until > now) return PinResult.LockedOut(until)

        val salt = prefs.getString(KEY_PIN_SALT, null)?.unb64() ?: return PinResult.Wrong(0)
        val stored = prefs.getString(KEY_PIN_HASH, null)?.unb64() ?: return PinResult.Wrong(0)
        if (MessageDigest.isEqual(stored, hash(pin, salt))) {
            prefs.edit { remove(KEY_FAILED); remove(KEY_LOCKOUT_UNTIL); remove(KEY_LOCKOUT_ROUNDS) }
            return PinResult.Ok
        }

        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        return if (failed >= MAX_ATTEMPTS) {
            // Cada ronda de fallos duplica la espera: 30 s, 60 s, 120 s…
            val rounds = prefs.getInt(KEY_LOCKOUT_ROUNDS, 0)
            val lockUntil = now + 30_000L * (1L shl rounds.coerceAtMost(6))
            prefs.edit {
                putInt(KEY_FAILED, 0)
                putInt(KEY_LOCKOUT_ROUNDS, rounds + 1)
                putLong(KEY_LOCKOUT_UNTIL, lockUntil)
            }
            PinResult.LockedOut(lockUntil)
        } else {
            prefs.edit { putInt(KEY_FAILED, failed) }
            PinResult.Wrong(MAX_ATTEMPTS - failed)
        }
    }

    private fun hash(pin: String, salt: ByteArray) = hmac.sign(salt + pin.toByteArray(Charsets.UTF_8))

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        const val PIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_FAILED = "pin_failed"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val KEY_LOCKOUT_ROUNDS = "pin_lockout_rounds"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_AUTO_LOCK = "auto_lock_seconds"
        private const val KEY_MAX_CHARS = "max_chars"
        private const val KEY_ALLOW_PAST = "allow_past_days"
        private const val KEY_SECURE = "secure_screen"
    }
}
