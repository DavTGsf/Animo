package com.example.animo.domain

import com.example.animo.data.DayEntry
import com.example.animo.data.Journal
import com.example.animo.data.Mood
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

enum class EffectSize(val label: String) {
    MUY_PEQUENO("muy pequeña"), PEQUENO("pequeña"), MODERADO("moderada"), GRANDE("grande");

    companion object {
        /** Umbrales convencionales de Cohen (0,2 / 0,5 / 0,8). */
        fun of(d: Double): EffectSize {
            val a = abs(d)
            return if (a < 0.2) MUY_PEQUENO else if (a < 0.5) PEQUENO else if (a < 0.8) MODERADO else GRANDE
        }
    }
}

data class ActivityStat(
    val name: String,
    /** n: días con la actividad. */
    val days: Int,
    val avgMood: Double,
    val avgEnergy: Double,
    /** Ánimo promedio con la actividad menos el promedio general. */
    val moodDelta: Double,
    val energyDelta: Double = 0.0,
    /** Días sin la actividad (grupo de comparación). */
    val daysWithout: Int = 0,
    /** Diferencia de medias: con la actividad menos sin ella. */
    val moodDiff: Double = 0.0,
    val energyDiff: Double = 0.0,
    val moodCI: Interval? = null,
    val energyCI: Interval? = null,
    val cohenD: Double = 0.0,
    val energyCohenD: Double = 0.0,
    val moodP: Double = 1.0,
    val energyP: Double = 1.0,
    /** Valores q ajustados con Benjamini-Hochberg. */
    val moodQ: Double = 1.0,
    val energyQ: Double = 1.0,
    val dates: List<LocalDate> = emptyList(),
) {
    val hasEnoughData: Boolean
        get() = days >= Insights.MIN_DAYS_FOR_PATTERN && daysWithout >= Insights.MIN_DAYS_FOR_PATTERN

    val isMoodPattern: Boolean
        get() = hasEnoughData && moodQ < Insights.FDR && abs(cohenD) >= Insights.MIN_EFFECT

    val isEnergyPattern: Boolean
        get() = hasEnoughData && energyQ < Insights.FDR && abs(energyCohenD) >= Insights.MIN_EFFECT

    val effect: EffectSize get() = EffectSize.of(cohenD)
}

data class Trend(
    val recentAvg: Double,
    val previousAvg: Double,
    val percentChange: Int,
    val windowDays: Int,
    val recentN: Int = 0,
    val previousN: Int = 0,
    val diffCI: Interval? = null,
    val cohenD: Double = 0.0,
) {
    val difference: Double get() = recentAvg - previousAvg

    /** Cambio claro: el IC 95 % excluye el 0 y el efecto es al menos pequeño. */
    val isClearChange: Boolean get() = diffCI?.excludesZero == true && abs(cohenD) >= 0.2
}

data class DayPoint(val date: LocalDate, val mood: Mood?, val energy: Int?)

enum class PeriodKind { SEMANA, MES }

data class PeriodSummary(
    val kind: PeriodKind,
    val start: LocalDate,
    val end: LocalDate,
    val daysInPeriod: Int,
    val daysRecorded: Int,
    val longestStreak: Int,
    val mood: Descriptive?,
    val energy: Descriptive?,
    /** Moda categórica (Enojado y Triste tienen el mismo puntaje, así que se cuenta por estado). */
    val frequentMoods: List<Mood>,
)

/** Arranque en frío: qué se muestra según la cantidad de días registrados. */
enum class DataLevel { SIN_DATOS, INICIAL, SUFICIENTE }

data class InsightsReport(
    val totalDays: Int,
    val level: DataLevel,
    val stats: List<ActivityStat>,
    val trend: Trend?,
    val weeks: List<PeriodSummary>,
    val months: List<PeriodSummary>,
    val energyMoodRho: Double?,
    val insights: List<GeneratedInsight>,
)

/**
 * Cálculos sobre los registros del usuario. No son diagnósticos.
 *
 * Método para relacionar actividad y ánimo: **diferencia de medias** entre días con y sin la
 * actividad, porque es la medida más fácil de explicar ("X puntos más alto"). Se acompaña de:
 * - una prueba de permutación, porque el ánimo es ordinal y las muestras son pequeñas (no se supone normalidad);
 * - la d de Cohen, para distinguir una diferencia pequeña de una relevante;
 * - un IC 95 % de Welch, para mostrar la incertidumbre;
 * - el ajuste de Benjamini-Hochberg, por las comparaciones múltiples.
 * Spearman se usa solo para energía contra ánimo (dos variables ordinales).
 */
object Insights {

    /** Mínimo de días para mostrar estadísticas descriptivas. */
    const val MIN_DAYS_TO_SHOW = 3
    /** Mínimo de días en total para buscar patrones. */
    const val MIN_TOTAL_DAYS = 14
    /** Mínimo de registros con (y sin) una actividad para compararla. */
    const val MIN_DAYS_FOR_PATTERN = 5
    /** Tasa de falsos descubrimientos aceptada. */
    const val FDR = 0.10
    /** Tamaño de efecto mínimo para reportar un patrón (moderado). */
    const val MIN_EFFECT = 0.5

    fun level(total: Int): DataLevel = when {
        total < MIN_DAYS_TO_SHOW -> DataLevel.SIN_DATOS
        total < MIN_TOTAL_DAYS -> DataLevel.INICIAL
        else -> DataLevel.SUFICIENTE
    }

    fun averageMood(entries: Collection<DayEntry>): Double? =
        entries.takeIf { it.isNotEmpty() }?.map { it.mood.score }?.average()

    fun averageEnergy(entries: Collection<DayEntry>): Double? =
        entries.takeIf { it.isNotEmpty() }?.map { it.energy.toDouble() }?.average()

    fun activityStats(entries: Collection<DayEntry>): List<ActivityStat> {
        val all = entries.toList()
        val overallMood = averageMood(all) ?: return emptyList()
        val overallEnergy = averageEnergy(all)!!
        val names = all.flatMap { it.activities }.distinct()

        val drafts = names.map { name ->
            val (yes, no) = all.partition { name in it.activities }
            val my = yes.map { it.mood.score }
            val mn = no.map { it.mood.score }
            val ey = yes.map { it.energy.toDouble() }
            val en = no.map { it.energy.toDouble() }
            val enough = yes.size >= MIN_DAYS_FOR_PATTERN && no.size >= MIN_DAYS_FOR_PATTERN
            ActivityStat(
                name = name,
                days = yes.size,
                avgMood = my.average(),
                avgEnergy = ey.average(),
                moodDelta = my.average() - overallMood,
                energyDelta = ey.average() - overallEnergy,
                daysWithout = no.size,
                moodDiff = if (mn.isEmpty()) 0.0 else my.average() - mn.average(),
                energyDiff = if (en.isEmpty()) 0.0 else ey.average() - en.average(),
                moodCI = if (enough) Stats.meanDiffCI(my, mn) else null,
                energyCI = if (enough) Stats.meanDiffCI(ey, en) else null,
                cohenD = if (enough) Stats.cohenD(my, mn) else 0.0,
                energyCohenD = if (enough) Stats.cohenD(ey, en) else 0.0,
                moodP = if (enough) Stats.permutationPValue(my, mn, seed = name.hashCode()) else 1.0,
                energyP = if (enough) Stats.permutationPValue(ey, en, seed = name.hashCode() + 1) else 1.0,
                dates = yes.map { it.date }.sorted(),
            )
        }

        // Una sola familia de pruebas (ánimo y energía de todas las actividades comparables).
        val tested = drafts.indices.filter { drafts[it].hasEnoughData }
        val q = Stats.benjaminiHochberg(tested.flatMap { listOf(drafts[it].moodP, drafts[it].energyP) })
        val adjusted = drafts.toMutableList()
        tested.forEachIndexed { k, i -> adjusted[i] = drafts[i].copy(moodQ = q[2 * k], energyQ = q[2 * k + 1]) }

        return adjusted.sortedWith(compareByDescending<ActivityStat> { it.avgMood }.thenByDescending { it.days })
    }

    /** Un punto por día (null si no hubo registro), del más antiguo al más reciente. */
    fun dailySeries(entries: Map<LocalDate, DayEntry>, today: LocalDate, days: Int = 30): List<DayPoint> =
        (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val e = entries[date]
            DayPoint(date, e?.mood, e?.energy)
        }

    /**
     * Media móvil del ánimo, hacia atrás, de [window] días, alineada con dailySeries(days).
     * Es null si la ventana tiene menos de la mitad de los días registrados.
     */
    fun movingAverage(entries: Map<LocalDate, DayEntry>, today: LocalDate, days: Int, window: Int): List<Double?> {
        val extended = dailySeries(entries, today, days + window - 1)
        return (window - 1 until extended.size).map { end ->
            val values = (end - window + 1..end).mapNotNull { extended[it].mood?.score }
            if (values.size >= (window + 1) / 2) values.average() else null
        }
    }

    /**
     * Compara el ánimo de los últimos [windowDays] días contra los [windowDays] anteriores.
     * Devuelve null si alguna ventana tiene menos de [MIN_DAYS_FOR_PATTERN] registros.
     */
    fun moodTrend(entries: Map<LocalDate, DayEntry>, today: LocalDate, windowDays: Int = 14): Trend? {
        val recentStart = today.minusDays(windowDays.toLong() - 1)
        val previousStart = recentStart.minusDays(windowDays.toLong())
        val recent = entries.values.filter { it.date in recentStart..today }.map { it.mood.score }
        val previous = entries.values.filter { it.date >= previousStart && it.date < recentStart }.map { it.mood.score }
        if (recent.size < MIN_DAYS_FOR_PATTERN || previous.size < MIN_DAYS_FOR_PATTERN) return null
        val r = recent.average()
        val p = previous.average()
        return Trend(
            recentAvg = r,
            previousAvg = p,
            percentChange = (((r - p) / p) * 100).roundToInt(),
            windowDays = windowDays,
            recentN = recent.size,
            previousN = previous.size,
            diffCI = Stats.meanDiffCI(recent, previous),
            cohenD = Stats.cohenD(recent, previous),
        )
    }

    /** Días consecutivos con registro terminando hoy (o ayer, si hoy aún no se registra). */
    fun streak(entries: Map<LocalDate, DayEntry>, today: LocalDate): Int {
        var day = if (entries.containsKey(today)) today else today.minusDays(1)
        var count = 0
        while (entries.containsKey(day)) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun longestStreak(dates: Collection<LocalDate>): Int {
        var best = 0
        var current = 0
        var prev: LocalDate? = null
        for (d in dates.sorted()) {
            current = if (prev != null && d == prev.plusDays(1)) current + 1 else 1
            best = maxOf(best, current)
            prev = d
        }
        return best
    }

    fun moodDistribution(entries: Collection<DayEntry>): Map<Mood, Int> =
        Mood.entries.associateWith { m -> entries.count { it.mood == m } }

    fun moodMode(entries: Collection<DayEntry>): List<Mood> {
        val counts = entries.groupingBy { it.mood }.eachCount()
        val max = counts.values.maxOrNull() ?: return emptyList()
        return Mood.entries.filter { counts[it] == max }
    }

    /** Resúmenes de las últimas [count] semanas (lunes a domingo) o meses, del más reciente al más antiguo. */
    fun periodSummaries(
        entries: Map<LocalDate, DayEntry>,
        today: LocalDate,
        kind: PeriodKind,
        count: Int,
    ): List<PeriodSummary> {
        val first = entries.keys.minOrNull() ?: return emptyList()
        var start = when (kind) {
            PeriodKind.SEMANA -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            PeriodKind.MES -> today.withDayOfMonth(1)
        }
        val result = mutableListOf<PeriodSummary>()
        repeat(count) {
            val fullEnd = if (kind == PeriodKind.SEMANA) start.plusDays(6) else start.plusMonths(1).minusDays(1)
            if (fullEnd.isBefore(first)) return result
            val end = minOf(fullEnd, today)
            val inPeriod = entries.values.filter { it.date in start..end }
            result += PeriodSummary(
                kind = kind,
                start = start,
                end = end,
                daysInPeriod = (end.toEpochDay() - start.toEpochDay() + 1).toInt(),
                daysRecorded = inPeriod.size,
                longestStreak = longestStreak(inPeriod.map { it.date }),
                mood = Stats.describe(inPeriod.map { it.mood.score }),
                energy = Stats.describe(inPeriod.map { it.energy.toDouble() }),
                frequentMoods = moodMode(inPeriod),
            )
            start = if (kind == PeriodKind.SEMANA) start.minusWeeks(1) else start.minusMonths(1)
        }
        return result
    }

    /** Spearman entre energía y ánimo. Null si hay menos de [MIN_TOTAL_DAYS] días. */
    fun energyMoodCorrelation(entries: Collection<DayEntry>): Double? {
        if (entries.size < MIN_TOTAL_DAYS) return null
        val list = entries.toList()
        return Stats.spearman(list.map { it.energy.toDouble() }, list.map { it.mood.score })
    }

    /** Calcula todo de una vez (pensado para ejecutarse fuera del hilo principal). */
    fun report(journal: Journal, today: LocalDate): InsightsReport {
        val entries = journal.entries
        val all = entries.values
        val stats = activityStats(all)
        val trend = moodTrend(entries, today)
        val rho = energyMoodCorrelation(all)
        return InsightsReport(
            totalDays = all.size,
            level = level(all.size),
            stats = stats,
            trend = trend,
            weeks = periodSummaries(entries, today, PeriodKind.SEMANA, 4),
            months = periodSummaries(entries, today, PeriodKind.MES, 3),
            energyMoodRho = rho,
            insights = InsightWriter.write(all, stats, trend),
        )
    }
}
