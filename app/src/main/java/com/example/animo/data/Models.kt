package com.example.animo.data

import java.time.LocalDate

/**
 * Estados de ánimo disponibles. [score] los ubica en una escala 1–10 para poder
 * promediarlos junto con la energía (que también va de 1 a 10).
 */
enum class Mood(val label: String, val score: Double) {
    ENOJADO("Enojado", 2.0),
    TRISTE("Triste", 2.0),
    DEPRIMIDO("Desanimado", 1.0),
    PREOCUPADO("Preocupado", 4.0),
    NEUTRAL("Neutral", 5.0),
    BIEN("Bien", 7.0),
    FELIZ("Feliz", 8.5),
    EXCELENTE("Excelente", 10.0);

    companion object {
        fun fromName(name: String): Mood? = entries.firstOrNull { it.name == name }
    }
}

/** Actividades predefinidas de "¿Qué hiciste hoy?". Las personalizadas se guardan por nombre. */
val DefaultActivities = listOf(
    "Trabajo",
    "Estudio",
    "Ejercicio",
    "Familia",
    "Amigos",
    "Entretenimiento",
    "Descanso",
    "Viaje",
    "Compras",
    "Tareas domésticas",
)

const val MIN_ENERGY = 1
const val MAX_ENERGY = 10
const val MAX_ACTIVITY_NAME = 24

/** Un registro por día. Las actividades se guardan por nombre para sobrevivir si se borra una etiqueta. */
data class DayEntry(
    val date: LocalDate,
    val mood: Mood,
    val energy: Int,
    val activities: List<String>,
    val reflection: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val wasEdited: Boolean get() = updatedAt - createdAt > 60_000
}

/** Todo lo que se guarda cifrado en disco. */
data class Journal(
    val entries: Map<LocalDate, DayEntry> = emptyMap(),
    val customActivities: List<String> = emptyList(),
) {
    val allActivities: List<String> get() = DefaultActivities + customActivities
}

data class AppSettings(
    val hasPin: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** Segundos en segundo plano antes de bloquear. 0 = al salir de la app. */
    val autoLockSeconds: Int = 60,
    val maxReflectionChars: Int = 500,
    val allowPastDays: Boolean = false,
    val secureScreen: Boolean = true,
)

val AutoLockOptions = listOf(0, 30, 60, 300, 900)
val MaxCharsOptions = listOf(140, 280, 500, 1000, 2000)
