package com.example.animo.domain

import com.example.animo.data.DayEntry
import com.example.animo.data.Journal
import com.example.animo.data.MAX_ENERGY
import com.example.animo.data.MIN_ENERGY
import com.example.animo.data.Mood
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Genera un diario con patrones plantados para comprobar que el análisis encuentra lo que debe.
 * - Ánimo: Ejercicio +2, Amigos +2, Trabajo −1,8.
 * - Energía: Ejercicio +1,5, Descanso +1,5, Trabajo −1,2.
 * - Compras, Familia, etc. no tienen efecto (no deberían reportarse).
 * - Viaje es raro (~3 %), así que casi nunca alcanza el mínimo de registros.
 */
object SyntheticData {

    val PLANTED_MOOD: Map<String, Double> = mapOf("Ejercicio" to 2.0, "Amigos" to 2.0, "Trabajo" to -1.8)
    val PLANTED_ENERGY: Map<String, Double> = mapOf("Ejercicio" to 1.5, "Descanso" to 1.5, "Trabajo" to -1.2)

    val FREQUENCY: Map<String, Double> = linkedMapOf(
        "Trabajo" to 0.55, "Estudio" to 0.30, "Ejercicio" to 0.35, "Familia" to 0.30, "Amigos" to 0.30,
        "Entretenimiento" to 0.40, "Descanso" to 0.30, "Viaje" to 0.03, "Compras" to 0.20, "Tareas domésticas" to 0.35,
    )

    fun generate(today: LocalDate, days: Int = 90, seed: Int = 7, skipProbability: Double = 0.10): Journal {
        val rnd = Random(seed)
        val entries = buildMap {
            for (back in days - 1 downTo 0) {
                val date = today.minusDays(back.toLong())
                if (back > 0 && rnd.nextDouble() < skipProbability) continue
                val acts = FREQUENCY.filter { rnd.nextDouble() < it.value }.keys.toList()
                val shared = rnd.gaussian()
                val moodScore = 5.0 + acts.sumOf { PLANTED_MOOD[it] ?: 0.0 } + 1.2 * shared + 0.6 * rnd.gaussian()
                val energy = (5.0 + acts.sumOf { PLANTED_ENERGY[it] ?: 0.0 } + 0.8 * shared + rnd.gaussian())
                    .roundToInt().coerceIn(MIN_ENERGY, MAX_ENERGY)
                val created = date.atTime(21, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                put(date, DayEntry(date, nearestMood(moodScore, rnd), energy, acts, "", created, created))
            }
        }
        return Journal(entries = entries)
    }

    private fun nearestMood(score: Double, rnd: Random): Mood {
        val s = score.coerceIn(1.0, 10.0)
        val best = Mood.entries.minOf { abs(it.score - s) }
        val ties = Mood.entries.filter { abs(it.score - s) == best }
        return ties[rnd.nextInt(ties.size)]
    }

    private fun Random.gaussian(): Double {
        val u1 = nextDouble().coerceAtLeast(1e-12)
        val u2 = nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2 * PI * u2)
    }
}

/** Métricas del módulo de IA: precisión, exhaustividad y F1 sobre patrones plantados, contra un baseline. */
object PatternEvaluation {

    data class Score(val precision: Double, val recall: Double, val f1: Double)

    fun score(expected: Set<String>, reported: Set<String>): Score {
        val tp = reported.count { it in expected }.toDouble()
        val precision = if (reported.isEmpty()) (if (expected.isEmpty()) 1.0 else 0.0) else tp / reported.size
        val recall = if (expected.isEmpty()) 1.0 else tp / expected.size
        val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        return Score(precision, recall, f1)
    }

    /** Lo que reporta el motor (umbral, tamaño de efecto y ajuste por comparaciones múltiples). */
    fun engine(stats: List<ActivityStat>): Set<String> = stats.filter { it.isMoodPattern }.map { it.name }.toSet()

    /** Baseline ingenuo: toda actividad que se aleje más de 0,2 puntos del promedio, sin umbral ni ajuste. */
    fun naiveBaseline(stats: List<ActivityStat>): Set<String> =
        stats.filter { abs(it.moodDelta) > 0.2 }.map { it.name }.toSet()
}
