package com.example.animo

import com.example.animo.data.DayEntry
import com.example.animo.data.Mood
import com.example.animo.domain.InsightKind
import com.example.animo.domain.InsightWriter
import com.example.animo.domain.Insights
import com.example.animo.domain.PatternEvaluation
import com.example.animo.domain.PeriodKind
import com.example.animo.domain.ResponsibleLanguage
import com.example.animo.domain.Stats
import com.example.animo.domain.SyntheticData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {

    private val today = LocalDate.of(2026, 9, 29) // martes

    private fun entry(daysAgo: Long, mood: Mood, energy: Int = 5, vararg acts: String) =
        today.minusDays(daysAgo).let { DayEntry(it, mood, energy, acts.toList(), "", 0, 0) }

    // ---------------- Estadísticas descriptivas (cálculo manual)

    @Test
    fun describe_matchesManualCalculation() {
        val d = Stats.describe(listOf(2.0, 7.0, 7.0, 10.0))!!
        assertEquals(6.5, d.mean, 1e-9)
        assertEquals(7.0, d.median, 1e-9)
        assertEquals(listOf(7.0), d.mode)
        assertEquals(Math.sqrt(11.0), d.sd, 1e-9) // (20,25 + 0,25 + 0,25 + 12,25) / 3 = 11
    }

    @Test
    fun benjaminiHochberg_matchesManualCalculation() {
        val q = Stats.benjaminiHochberg(listOf(0.01, 0.04, 0.03, 0.20))
        assertEquals(0.04, q[0], 1e-9)
        assertEquals(0.16 / 3, q[1], 1e-9)
        assertEquals(0.16 / 3, q[2], 1e-9)
        assertEquals(0.20, q[3], 1e-9)
    }

    @Test
    fun spearman_isOneForMonotonicData() {
        assertEquals(1.0, Stats.spearman(listOf(1.0, 2.0, 3.0, 4.0), listOf(1.0, 4.0, 9.0, 16.0))!!, 1e-9)
    }

    @Test
    fun periodSummaries_weekStartsOnMonday() {
        val entries = listOf(
            entry(1, Mood.BIEN, 4),     // lunes 28
            entry(0, Mood.FELIZ, 6),    // martes 29
            entry(8, Mood.TRISTE, 3),   // lunes 21
        ).associateBy { it.date }
        val weeks = Insights.periodSummaries(entries, today, PeriodKind.SEMANA, 4)
        assertEquals(2, weeks.size)
        val w = weeks.first()
        assertEquals(LocalDate.of(2026, 9, 28), w.start)
        assertEquals(2, w.daysRecorded)
        assertEquals(2, w.daysInPeriod)
        assertEquals(2, w.longestStreak)
        assertEquals(7.75, w.mood!!.mean, 1e-9)
        assertEquals(listOf(4.0, 6.0), w.energy!!.mode)
    }

    @Test
    fun movingAverage_needsHalfTheWindow() {
        val entries = listOf(entry(0, Mood.EXCELENTE), entry(1, Mood.NEUTRAL)).associateBy { it.date }
        val ma = Insights.movingAverage(entries, today, days = 7, window = 3)
        assertEquals(7, ma.size)
        assertEquals(7.5, ma.last()!!, 1e-9)
        assertNull(ma.first())
    }

    // ---------------- Actividad vs. ánimo

    @Test
    fun activityStats_averagesMoodAndEnergyPerActivity() {
        val entries = listOf(
            entry(0, Mood.EXCELENTE, 9, "Ejercicio", "Trabajo"),
            entry(1, Mood.BIEN, 7, "Ejercicio"),
            entry(2, Mood.NEUTRAL, 4, "Trabajo"),
        )
        val stats = Insights.activityStats(entries).associateBy { it.name }

        assertEquals(8.5, stats.getValue("Ejercicio").avgMood, 0.001)
        assertEquals(8.0, stats.getValue("Ejercicio").avgEnergy, 0.001)
        assertEquals(7.5, stats.getValue("Trabajo").avgMood, 0.001)
        assertEquals(2, stats.getValue("Trabajo").days)
        assertEquals(8.5 - 22.0 / 3, stats.getValue("Ejercicio").moodDelta, 0.001)
        assertEquals("Ejercicio", Insights.activityStats(entries).first().name)
    }

    @Test
    fun fewObservations_areNeverReportedAsPattern() {
        // "Viaje" aparece 4 días, todos excelentes: diferencia enorme, pero n insuficiente.
        val entries = (0L until 20L).map { d ->
            if (d < 4) entry(d, Mood.EXCELENTE, 9, "Viaje") else entry(d, Mood.NEUTRAL, 5)
        }
        val viaje = Insights.activityStats(entries).first { it.name == "Viaje" }
        assertFalse(viaje.hasEnoughData)
        assertFalse(viaje.isMoodPattern)
        val texts = Insights.report(com.example.animo.data.Journal(entries.associateBy { it.date }), today).insights
        assertTrue(texts.none { it.kind == InsightKind.RELACION && "Viaje" in it.text })
    }

    // ---------------- Tendencia

    @Test
    fun moodTrend_comparesLastTwoWeeksAgainstPreviousTwo() {
        val recent = (0L..4L).map { entry(it, Mood.FELIZ) }          // 8,5
        val previous = (14L..18L).map { entry(it, Mood.BIEN) }       // 7,0
        val trend = Insights.moodTrend((recent + previous).associateBy { it.date }, today)!!
        assertEquals(21, trend.percentChange) // (8,5 − 7) / 7 = 21,4 %
        assertEquals(1.5, trend.difference, 1e-9)
        assertTrue(trend.isClearChange)
    }

    @Test
    fun moodTrend_needsEnoughDataInBothWindows() {
        val entries = listOf(entry(1, Mood.FELIZ), entry(2, Mood.FELIZ), entry(16, Mood.BIEN))
            .associateBy { it.date }
        assertNull(Insights.moodTrend(entries, today))
    }

    @Test
    fun streak_countsConsecutiveDaysEvenIfTodayIsPending() {
        val entries = listOf(entry(1, Mood.BIEN), entry(2, Mood.BIEN), entry(4, Mood.BIEN))
            .associateBy { it.date }
        assertEquals(2, Insights.streak(entries, today))
    }

    @Test
    fun dailySeries_hasOnePointPerDayWithGaps() {
        val entries = listOf(entry(0, Mood.FELIZ), entry(29, Mood.TRISTE)).associateBy { it.date }
        val series = Insights.dailySeries(entries, today)
        assertEquals(30, series.size)
        assertEquals(Mood.TRISTE, series.first().mood)
        assertEquals(Mood.FELIZ, series.last().mood)
        assertNull(series[10].mood)
    }

    // ---------------- Datos sintéticos y métrica contra baseline

    @Test
    fun syntheticData_engineFindsPlantedPatternsAndBeatsBaseline() {
        val expected = SyntheticData.PLANTED_MOOD.keys
        val scores = (1..10).map { seed ->
            val journal = SyntheticData.generate(today, seed = seed)
            val stats = Insights.activityStats(journal.entries.values)
            PatternEvaluation.score(expected, PatternEvaluation.engine(stats)) to
                    PatternEvaluation.score(expected, PatternEvaluation.naiveBaseline(stats))
        }
        val engineF1 = scores.map { it.first.f1 }.average()
        val baselineF1 = scores.map { it.second.f1 }.average()
        println("F1 motor = $engineF1 · F1 baseline = $baselineF1")
        assertTrue("El motor debe superar al baseline", engineF1 > baselineF1)
        assertTrue(engineF1 >= 0.8)
    }

    @Test
    fun syntheticData_rareActivitiesAreNotReported() {
        (1..10).forEach { seed ->
            val stats = Insights.activityStats(SyntheticData.generate(today, seed = seed).entries.values)
            stats.filter { it.days < Insights.MIN_DAYS_FOR_PATTERN }.forEach { assertFalse(it.isMoodPattern) }
        }
    }

    // ---------------- Lenguaje responsable (módulo D)

    @Test
    fun generatedInsights_neverClaimCausalityOrDiagnosis() {
        val journals = (1..10).map { SyntheticData.generate(today, seed = it) } +
                SyntheticData.generate(today, days = 10, seed = 99)
        journals.forEach { j ->
            Insights.report(j, today).insights.forEach { insight ->
                assertTrue("${insight.text} → ${ResponsibleLanguage.check(insight)}", ResponsibleLanguage.check(insight).isEmpty())
            }
        }
        listOf(0.05, 0.25, -0.4, 0.8).forEach { rho ->
            val s = InsightWriter.correlationSentence(rho, 30)
            assertTrue(s, ResponsibleLanguage.violations(s).isEmpty() && ResponsibleLanguage.isHedged(s))
        }
    }

    @Test
    fun responsibleLanguage_detectsBadPhrases() {
        listOf(
            "Hacer ejercicio mejora tu ánimo.",
            "El trabajo te provoca tristeza.",
            "Tienes síntomas de depresión.",
            "Siempre que sales con amigos estás feliz.",
            "Deberías descansar más.",
        ).forEach { assertTrue(it, ResponsibleLanguage.violations(it).isNotEmpty()) }
    }

    @Test
    fun coldStart_showsNotEnoughDataMessage() {
        val j = SyntheticData.generate(today, days = 5, seed = 3)
        val insights = Insights.report(j, today).insights
        assertTrue(insights.any { it.kind == InsightKind.AVISO_DATOS && it.text.startsWith("Aún no hay datos suficientes") })
        assertTrue(insights.none { it.kind == InsightKind.RELACION || it.kind == InsightKind.RECOMENDACION })
    }
}
