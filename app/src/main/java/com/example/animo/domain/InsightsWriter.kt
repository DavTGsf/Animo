package com.example.animo.domain

import com.example.animo.data.DayEntry
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

enum class InsightKind { AVISO_DATOS, RESUMEN, TENDENCIA, RELACION, RECOMENDACION }

/** Explicabilidad: en qué se basa cada frase ("¿por qué veo esto?"). */
data class Evidence(
    val n: Int,
    val comparisonN: Int? = null,
    val method: String,
    val details: String = "",
    val dates: List<LocalDate> = emptyList(),
)

data class GeneratedInsight(val kind: InsightKind, val text: String, val evidence: Evidence)

/**
 * Módulo de IA D: convierte resultados numéricos en frases mediante plantillas.
 * Se eligieron plantillas en lugar de un modelo de lenguaje porque garantizan que el texto
 * nunca afirme causalidad ni diagnósticos, funcionan sin conexión y son verificables con pruebas.
 */
object InsightWriter {

    private val locale: Locale = Locale.forLanguageTag("es-MX")
    private fun Double.f1() = String.format(locale, "%.1f", this)
    private fun Double.f2() = String.format(locale, "%.2f", this)

    const val MAX_RELATIONS = 3
    const val MAX_RECOMMENDATIONS = 2

    private const val METHOD_ACTIVITY =
        "Diferencia de medias entre días con y sin la actividad, prueba de permutación, " +
                "d de Cohen y ajuste de Benjamini-Hochberg por comparaciones múltiples."

    fun write(entries: Collection<DayEntry>, stats: List<ActivityStat>, trend: Trend?): List<GeneratedInsight> = buildList {
        val total = entries.size
        if (total == 0) return@buildList
        val dates = entries.map { it.date }.sorted()

        add(summary(entries, dates))

        if (total < Insights.MIN_TOTAL_DAYS) {
            add(
                GeneratedInsight(
                    InsightKind.AVISO_DATOS,
                    "Aún no hay datos suficientes para buscar relaciones: llevas $total de ${Insights.MIN_TOTAL_DAYS} días registrados.",
                    Evidence(
                        n = total,
                        method = "Umbral mínimo: ${Insights.MIN_TOTAL_DAYS} días en total y " +
                                "${Insights.MIN_DAYS_FOR_PATTERN} registros por actividad.",
                        dates = dates,
                    ),
                )
            )
            return@buildList
        }

        trend?.let { add(trend(it)) }

        val relations = stats.filter { it.isMoodPattern }.sortedByDescending { abs(it.cohenD) }.take(MAX_RELATIONS)
        relations.forEach { add(relation(it)) }
        if (relations.isEmpty()) add(noRelation(stats.count { it.hasEnoughData }, total, dates))

        stats.filter { it.isEnergyPattern && it.energyDiff > 0 }
            .sortedByDescending { it.energyCohenD }
            .take(MAX_RECOMMENDATIONS)
            .forEach { add(recommendation(it)) }
    }

    private fun summary(entries: Collection<DayEntry>, dates: List<LocalDate>): GeneratedInsight {
        val modes = Insights.moodMode(entries)
        val verb = if (modes.size > 1) "los estados que más se repiten son" else "el estado que más se repite es"
        return GeneratedInsight(
            InsightKind.RESUMEN,
            "En tus ${entries.size} registros, $verb ${modes.joinToString(" y ") { it.label }}.",
            Evidence(n = entries.size, method = "Moda del estado de ánimo (el valor más frecuente).", dates = dates),
        )
    }

    fun trendSentence(t: Trend): String = when {
        !t.isClearChange ->
            "No se observa un cambio claro en tu ánimo entre las últimas dos semanas (${t.recentAvg.f1()}) " +
                    "y las dos anteriores (${t.previousAvg.f1()})."
        t.difference > 0 ->
            "Se observa que tu ánimo promedio de las últimas dos semanas fue ${t.difference.f1()} puntos más alto que en las dos semanas anteriores."
        else ->
            "Se observa que tu ánimo promedio de las últimas dos semanas fue ${abs(t.difference).f1()} puntos más bajo que en las dos semanas anteriores."
    }

    private fun trend(t: Trend) = GeneratedInsight(
        InsightKind.TENDENCIA,
        trendSentence(t),
        Evidence(
            n = t.recentN,
            comparisonN = t.previousN,
            method = "Diferencia de medias entre dos ventanas de ${t.windowDays} días con intervalo de confianza del 95 % (Welch).",
            details = buildString {
                t.diffCI?.let { append("IC 95 %: ${it.low.f1()} a ${it.high.f1()} · ") }
                append("tamaño de la diferencia: ${EffectSize.of(t.cohenD).label}")
            },
        ),
    )

    private fun relation(s: ActivityStat): GeneratedInsight {
        val direction = if (s.moodDiff > 0) "más alto" else "más bajo"
        val without = s.avgMood - s.moodDiff
        return GeneratedInsight(
            InsightKind.RELACION,
            "Se observa una relación entre ${s.name} y tu ánimo: los días con esta actividad tu ánimo fue en promedio " +
                    "${abs(s.moodDiff).f1()} puntos $direction que los días sin ella (diferencia ${s.effect.label}).",
            Evidence(
                n = s.days,
                comparisonN = s.daysWithout,
                method = METHOD_ACTIVITY,
                details = buildString {
                    append("Con: ${s.avgMood.f1()} · Sin: ${without.f1()}")
                    s.moodCI?.let { append(" · IC 95 % de la diferencia: ${it.low.f1()} a ${it.high.f1()}") }
                    append(" · d = ${s.cohenD.f2()} · q = ${s.moodQ.f2()}")
                },
                dates = s.dates,
            ),
        )
    }

    private fun noRelation(tested: Int, total: Int, dates: List<LocalDate>): GeneratedInsight {
        val text = if (tested == 0) {
            "Aún no hay datos suficientes para comparar actividades: ninguna tiene todavía " +
                    "${Insights.MIN_DAYS_FOR_PATTERN} días con ella y ${Insights.MIN_DAYS_FOR_PATTERN} sin ella."
        } else {
            "Con los datos actuales no se observa una diferencia clara en tu ánimo asociada a alguna actividad " +
                    "(se revisaron $tested ${if (tested == 1) "actividad" else "actividades"})."
        }
        return GeneratedInsight(
            if (tested == 0) InsightKind.AVISO_DATOS else InsightKind.RELACION,
            text,
            Evidence(n = total, method = METHOD_ACTIVITY, dates = dates),
        )
    }

    /** Módulo E: sugerencia basada en días con mayor energía, con intervalo de confianza simple. */
    private fun recommendation(s: ActivityStat): GeneratedInsight {
        val without = s.avgEnergy - s.energyDiff
        val ci = s.energyCI?.let { " (IC 95 % de la diferencia: ${it.low.f1()} a ${it.high.f1()})" }.orEmpty()
        return GeneratedInsight(
            InsightKind.RECOMENDACION,
            "Se observa que los días con ${s.name} tu energía fue mayor: ${s.avgEnergy.f1()} frente a ${without.f1()}$ci. " +
                    "Puede ser algo a tener en cuenta al planear tu semana.",
            Evidence(
                n = s.days,
                comparisonN = s.daysWithout,
                method = METHOD_ACTIVITY,
                details = "d = ${s.energyCohenD.f2()} · q = ${s.energyQ.f2()}",
                dates = s.dates,
            ),
        )
    }

    fun correlationSentence(rho: Double, n: Int): String {
        val a = abs(rho)
        if (a < 0.1) return "No se observa una relación clara entre tu energía y tu ánimo (ρ de Spearman = ${rho.f2()}, n = $n)."
        val strength = when {
            a < 0.3 -> "débil"
            a < 0.5 -> "moderada"
            else -> "fuerte"
        }
        val sign = if (rho > 0) "positiva" else "negativa"
        val follow = if (rho > 0) "suele subir también" else "suele bajar"
        return "Se observa una relación $strength y $sign entre tu energía y tu ánimo (ρ de Spearman = ${rho.f2()}, n = $n): " +
                "en tus registros, cuando una sube, la otra $follow."
    }
}

/** Verificador de lenguaje responsable: sin causalidad, sin diagnósticos, sin absolutos ni órdenes. */
object ResponsibleLanguage {

    /** Se buscan como inicio de palabra ("depresi" detecta depresión y depresivo). */
    val FORBIDDEN = listOf(
        "causa", "provoca", "debido a", "por culpa", "gracias a", "porque", "hace que", "te hace",
        "mejora tu", "empeora tu", "deberías", "debes", "tienes que",
        "depresi", "ansiedad", "trastorno", "diagnóstic", "síntoma", "bipolar",
        "siempre", "nunca", "garantiza", "comprobado", "demuestra",
        "puntuación", "calificación",
    )

    private val HEDGES = listOf("se observa", "se observó")
    private val needsHedge = setOf(InsightKind.TENDENCIA, InsightKind.RELACION, InsightKind.RECOMENDACION)

    fun violations(text: String): List<String> {
        val t = text.lowercase(Locale.ROOT)
        return FORBIDDEN.filter { Regex("(?<!\\p{L})" + Regex.escape(it)).containsMatchIn(t) }
    }

    fun isHedged(text: String): Boolean = HEDGES.any { it in text.lowercase(Locale.ROOT) }

    fun check(insight: GeneratedInsight): List<String> =
        violations(insight.text) +
                if (insight.kind in needsHedge && !isHedged(insight.text)) listOf("falta \"se observa\"") else emptyList()
}
