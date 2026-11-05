package com.example.animo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ScatterPlot
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.animo.data.DayEntry
import com.example.animo.data.Journal
import com.example.animo.data.Mood
import com.example.animo.domain.ActivityStat
import com.example.animo.domain.DataLevel
import com.example.animo.domain.DayPoint
import com.example.animo.domain.GeneratedInsight
import com.example.animo.domain.InsightKind
import com.example.animo.domain.InsightWriter
import com.example.animo.domain.Insights
import com.example.animo.domain.InsightsReport
import com.example.animo.domain.PeriodKind
import com.example.animo.domain.PeriodSummary
import com.example.animo.domain.Trend
import com.example.animo.ui.components.CartoonCard
import com.example.animo.ui.components.CartoonChip
import com.example.animo.ui.components.EmptyState
import com.example.animo.ui.components.MoodFace
import com.example.animo.ui.components.SectionTitle
import com.example.animo.ui.components.activityVisual
import com.example.animo.ui.components.cartoonSurface
import com.example.animo.ui.components.dayMonthLabel
import com.example.animo.ui.components.inkOn
import com.example.animo.ui.components.label
import com.example.animo.ui.components.oneDecimal
import com.example.animo.ui.components.shortLabel
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette
import com.example.animo.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ChartRange(val days: Int, val label: String, val window: Int) {
    SEMANA(7, "7 días", 3),
    MES(30, "30 días", 7),
    TRIMESTRE(90, "90 días", 7),
}

@Composable
fun InsightsScreen(journal: Journal, today: LocalDate, contentPadding: PaddingValues) {
    // Los agregados (incluidas las pruebas de permutación) se calculan fuera del hilo principal
    // y se conservan hasta que cambie el diario o la fecha.
    val report by produceState<InsightsReport?>(initialValue = null, journal, today) {
        value = withContext(Dispatchers.Default) { Insights.report(journal, today) }
    }
    val total = journal.entries.size

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Text("Tus patrones", style = MaterialTheme.typography.displaySmall, color = AnimoTheme.colors.text)
                Text(
                    "Lo que tus propios registros dicen de ti",
                    style = MaterialTheme.typography.titleSmall,
                    color = AnimoTheme.colors.textMuted,
                )
            }
        }
        item { Disclaimer() }

        if (total < Insights.MIN_DAYS_TO_SHOW) {
            item {
                CartoonCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        title = "Aún no hay datos suficientes",
                        message = "Registra al menos ${Insights.MIN_DAYS_TO_SHOW} días para empezar a ver tus estadísticas. ¡Llevas $total!",
                        mood = Mood.BIEN,
                    )
                }
            }
            return@LazyColumn
        }

        val r = report
        if (r == null) {
            item { Text("Calculando…", style = MaterialTheme.typography.bodyMedium, color = AnimoTheme.colors.textMuted) }
            return@LazyColumn
        }

        item { SummaryTiles(journal, today) }
        item { GeneratedInsightsCard(r.insights) }
        item { TrendCard(r.trend) }
        item { TrendChartCard(journal, today) }
        item { PeriodStatsCard(r.weeks, r.months) }

        if (r.level != DataLevel.SUFICIENTE) {
            item { NotEnoughDataCard(total) }
        } else {
            item {
                CartoonCard(Modifier.fillMaxWidth()) {
                    SectionTitle("¿Cómo me siento según lo que hago?", icon = Icons.Rounded.Mood, tint = Palette.Mint)
                    Text(
                        "Ánimo promedio (1–10) de los días con cada actividad y diferencia contra tu media general.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnimoTheme.colors.textMuted,
                    )
                    Spacer(Modifier.height(14.dp))
                    ActivityBars(r.stats.sortedByDescending { it.avgMood }, { it.avgMood }, { it.moodDelta })
                }
            }
            item {
                CartoonCard(Modifier.fillMaxWidth()) {
                    SectionTitle("¿Con qué actividades tengo más energía?", icon = Icons.Rounded.Bolt, tint = Palette.Tangerine)
                    Text(
                        "Energía promedio (1–10) de los días con cada actividad y diferencia contra tu media general.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnimoTheme.colors.textMuted,
                    )
                    Spacer(Modifier.height(14.dp))
                    ActivityBars(r.stats.sortedByDescending { it.avgEnergy }, { it.avgEnergy }, { it.energyDelta })
                }
            }
        }
        item { ScatterCard(journal.entries.values, r.energyMoodRho) }
        item { DistributionCard(journal) }
    }
}

@Composable
private fun Disclaimer() {
    val colors = AnimoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .cartoonSurface(colors.surfaceAlt, colors.outline, RoundedCornerShape(18.dp), 0.dp, 2.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Info, null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Ánimo no realiza diagnósticos médicos ni sustituye atención profesional. " +
                    "Solo te ayuda a ver patrones en tus propios datos.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.text,
        )
    }
}

@Composable
private fun SummaryTiles(journal: Journal, today: LocalDate) {
    val all = journal.entries.values
    val avgMood = Insights.averageMood(all) ?: 0.0
    val avgEnergy = Insights.averageEnergy(all) ?: 0.0
    val nearest = Mood.entries.minBy { abs(it.score - avgMood) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= 560.dp) 4 else 2
        val tiles = listOf<@Composable (Modifier) -> Unit>(
            { m -> StatTile("Ánimo promedio", avgMood.oneDecimal(), Palette.Pink, m) { MoodFace(nearest, Modifier.size(30.dp)) } },
            { m -> StatTile("Energía promedio", avgEnergy.oneDecimal(), Palette.Sun, m) { TileIcon(Icons.Rounded.Bolt) } },
            { m -> StatTile("Días registrados", "${all.size}", Palette.Sky, m) { TileIcon(Icons.Rounded.CalendarMonth) } },
            { m -> StatTile("Racha actual", "${Insights.streak(journal.entries, today)}", Palette.Tangerine, m) { TileIcon(Icons.Rounded.LocalFireDepartment) } },
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tiles.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { it(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TileIcon(icon: ImageVector) = Icon(icon, null, tint = Palette.Ink, modifier = Modifier.size(26.dp))

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier, icon: @Composable () -> Unit) {
    CartoonCard(modifier, color = color, contentPadding = PaddingValues(14.dp), shadow = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
            icon()
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Ink)
    }
}

// ------------------------------------------------------------------ Insights generados (módulo D)

@Composable
private fun GeneratedInsightsCard(insights: List<GeneratedInsight>) {
    val colors = AnimoTheme.colors
    CartoonCard(Modifier.fillMaxWidth()) {
        SectionTitle("Lo que se observa en tus datos", icon = Icons.Rounded.AutoAwesome, tint = Palette.Sun)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            insights.forEach { InsightRow(it) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Que dos cosas ocurran juntas no significa que una provoque la otra. Toca una frase para ver en qué datos se basa.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun InsightRow(insight: GeneratedInsight) {
    val colors = AnimoTheme.colors
    var open by rememberSaveable(insight.text) { mutableStateOf(false) }
    val (tint, icon) = when (insight.kind) {
        InsightKind.AVISO_DATOS -> Palette.Lilac to Icons.Rounded.HourglassEmpty
        InsightKind.RESUMEN -> Palette.Sky to Icons.Rounded.PieChart
        InsightKind.TENDENCIA -> Palette.Pink to Icons.Rounded.ShowChart
        InsightKind.RELACION -> Palette.Mint to Icons.Rounded.Link
        InsightKind.RECOMENDACION -> Palette.Sun to Icons.Rounded.Lightbulb
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .cartoonSurface(tint.copy(alpha = 0.3f), colors.outline, shape, 0.dp, 2.dp)
            .clip(shape)
            .clickable { open = !open }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = colors.text, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(insight.text, style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿Por qué veo esto?", style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(open) {
            val e = insight.evidence
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    buildString {
                        append("Basado en ${e.n} ${if (e.n == 1) "registro" else "registros"}")
                        e.comparisonN?.let { append(", comparados con $it ${if (it == 1) "registro" else "registros"}") }
                        append(".")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text,
                )
                Text(e.method, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                if (e.details.isNotBlank()) Text(e.details, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                if (e.dates.isNotEmpty()) {
                    val shown = e.dates.takeLast(8)
                    Text(
                        "Días: " + shown.joinToString(", ") { it.shortLabel() } +
                                if (e.dates.size > shown.size) " y ${e.dates.size - shown.size} más" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotEnoughDataCard(total: Int) {
    CartoonCard(Modifier.fillMaxWidth()) {
        EmptyState(
            title = "Aún no hay datos suficientes",
            message = "Para comparar actividades se necesitan al menos ${Insights.MIN_TOTAL_DAYS} días registrados y " +
                    "${Insights.MIN_DAYS_FOR_PATTERN} registros por actividad. Llevas $total ${if (total == 1) "día" else "días"}.",
            mood = Mood.BIEN,
        )
    }
}

// ------------------------------------------------------------------ Tendencia

@Composable
private fun TrendCard(trend: Trend?) {
    val colors = AnimoTheme.colors
    val (icon, tint) = when {
        trend == null -> Icons.AutoMirrored.Rounded.TrendingFlat to Palette.Lilac
        !trend.isClearChange -> Icons.AutoMirrored.Rounded.TrendingFlat to Palette.Sky
        trend.difference > 0 -> Icons.AutoMirrored.Rounded.TrendingUp to Palette.Mint
        else -> Icons.AutoMirrored.Rounded.TrendingDown to Palette.Lilac
    }
    val text = trend?.let { InsightWriter.trendSentence(it) }
        ?: "Aún no hay datos suficientes: se necesitan al menos ${Insights.MIN_DAYS_FOR_PATTERN} registros en cada una de tus últimas dos quincenas para compararlas."
    CartoonCard(Modifier.fillMaxWidth(), color = colors.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).cartoonSurface(tint, colors.outline, RoundedCornerShape(18.dp), 3.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = inkOn(tint), modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Tendencia", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Text(text, style = MaterialTheme.typography.titleMedium, color = colors.text)
                if (trend != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Últimos 14 días: ${trend.recentAvg.oneDecimal()} (n = ${trend.recentN}) · " +
                                "14 días anteriores: ${trend.previousAvg.oneDecimal()} (n = ${trend.previousN})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendChartCard(journal: Journal, today: LocalDate) {
    val colors = AnimoTheme.colors
    var range by rememberSaveable { mutableStateOf(ChartRange.MES) }
    val points = remember(journal.entries, today, range) { Insights.dailySeries(journal.entries, today, range.days) }
    val average = remember(journal.entries, today, range) {
        Insights.movingAverage(journal.entries, today, range.days, range.window)
    }
    CartoonCard(Modifier.fillMaxWidth()) {
        SectionTitle("¿Cómo ha cambiado mi ánimo?", icon = Icons.Rounded.ShowChart, tint = Palette.Pink)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartRange.entries.forEach { r ->
                CartoonChip(r.label, r == range, { range = r }, selectedColor = Palette.Pink)
            }
        }
        Spacer(Modifier.height(12.dp))
        MoodLineChart(points, average, Modifier.fillMaxWidth().height(210.dp))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(Palette.Grape, "Ánimo diario")
            Spacer(Modifier.width(16.dp))
            LegendDot(Palette.Tangerine, "Media móvil (${range.window} días)")
        }
        Text(
            "${points.count { it.mood != null }} días registrados en este rango",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22.dp, height = 10.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AnimoTheme.colors.textMuted)
    }
}

/** Línea de ánimo (1–10) con puntos del color de cada estado y su media móvil. */
@Composable
private fun MoodLineChart(points: List<DayPoint>, average: List<Double?>, modifier: Modifier) {
    val colors = AnimoTheme.colors
    val measurer = rememberTextMeasurer()
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted)
    val recorded = points.count { it.mood != null }

    Canvas(modifier.semantics { contentDescription = "Gráfica de ánimo de los últimos ${points.size} días, $recorded días registrados" }) {
        val left = 26.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val w = right - left
        val h = bottom - top
        val dotR = if (points.size > 45) 3.5.dp.toPx() else 6.dp.toPx()
        fun x(i: Int) = left + w * i / (points.size - 1).coerceAtLeast(1)
        fun y(v: Double) = bottom - h * ((v - 1.0) / 9.0).toFloat()

        listOf(1, 4, 7, 10).forEach { v ->
            val yy = y(v.toDouble())
            drawLine(
                colors.textMuted.copy(alpha = 0.25f), Offset(left, yy), Offset(right, yy),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val t = measurer.measure("$v", labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), yy - t.size.height / 2))
        }
        listOf(0, points.size / 2, points.lastIndex).distinct().forEach { i ->
            val t = measurer.measure(if (i == points.lastIndex) "Hoy" else points[i].date.shortLabel(), labelStyle)
            val xx = (x(i) - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(xx, bottom + 4.dp.toPx()))
        }

        clipRect(right = left + (w + 8.dp.toPx()) * reveal.value) {
            val mood = Path()
            var started = false
            points.forEachIndexed { i, p ->
                val m = p.mood ?: return@forEachIndexed
                if (!started) { mood.moveTo(x(i), y(m.score)); started = true } else mood.lineTo(x(i), y(m.score))
            }
            drawPath(mood, Palette.Grape.copy(alpha = 0.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val ma = Path()
            var drawing = false
            average.forEachIndexed { i, v ->
                if (v == null) { drawing = false; return@forEachIndexed }
                if (!drawing) { ma.moveTo(x(i), y(v)); drawing = true } else ma.lineTo(x(i), y(v))
            }
            drawPath(ma, colors.outline, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(ma, Palette.Tangerine, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            points.forEachIndexed { i, p ->
                val m = p.mood ?: return@forEachIndexed
                val c = Offset(x(i), y(m.score))
                drawCircle(m.color, dotR, c)
                drawCircle(colors.outline, dotR, c, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

// ------------------------------------------------------------------ Estadísticas por periodo

@Composable
private fun PeriodStatsCard(weeks: List<PeriodSummary>, months: List<PeriodSummary>) {
    val colors = AnimoTheme.colors
    var kind by rememberSaveable { mutableStateOf(PeriodKind.SEMANA) }
    val periods = if (kind == PeriodKind.SEMANA) weeks else months
    CartoonCard(Modifier.fillMaxWidth()) {
        SectionTitle("¿Cómo me fue cada semana o mes?", icon = Icons.Rounded.TableChart, tint = Palette.Lilac)
        Text(
            "Ánimo y energía en escala 1–10. n = días con registro.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CartoonChip("Por semana", kind == PeriodKind.SEMANA, { kind = PeriodKind.SEMANA }, selectedColor = Palette.Lilac)
            CartoonChip("Por mes", kind == PeriodKind.MES, { kind = PeriodKind.MES }, selectedColor = Palette.Lilac)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            periods.forEach { PeriodRow(it) }
        }
    }
}

@Composable
private fun PeriodRow(p: PeriodSummary) {
    val colors = AnimoTheme.colors
    val title = when (p.kind) {
        PeriodKind.SEMANA -> "Semana del ${p.start.dayMonthLabel()}"
        PeriodKind.MES -> YearMonth.from(p.start).label()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .cartoonSurface(colors.surfaceAlt, colors.outline, RoundedCornerShape(16.dp), 0.dp, 2.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f))
            Text("n = ${p.daysRecorded} de ${p.daysInPeriod} días", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
        val mood = p.mood
        val energy = p.energy
        if (mood == null || energy == null) {
            Text("Sin registros en este periodo.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        } else {
            fun sd(v: Double, n: Int) = if (n < 2) "—" else v.oneDecimal()
            StatLine("Ánimo", "media ${mood.mean.oneDecimal()} · mediana ${mood.median.oneDecimal()} · desv. ${sd(mood.sd, mood.n)}")
            StatLine("Más frecuente", p.frequentMoods.joinToString(" / ") { it.label })
            StatLine(
                "Energía",
                "media ${energy.mean.oneDecimal()} · mediana ${energy.median.oneDecimal()} · " +
                        "moda ${energy.mode.joinToString(", ") { it.roundToInt().toString() }} · desv. ${sd(energy.sd, energy.n)}",
            )
            StatLine("Racha más larga", "${p.longestStreak} ${if (p.longestStreak == 1) "día" else "días"}")
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    val colors = AnimoTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.text)
    }
}

// ------------------------------------------------------------------ Actividades

private fun Double.signed(): String = (if (this >= 0) "+" else "−") + abs(this).oneDecimal()

/** Barras horizontales con n y diferencia contra la media general. */
@Composable
private fun ActivityBars(stats: List<ActivityStat>, value: (ActivityStat) -> Double, delta: (ActivityStat) -> Double) {
    val colors = AnimoTheme.colors
    if (stats.isEmpty()) {
        Text("Aún no has marcado actividades en tus registros.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEachIndexed { index, s ->
            val v = value(s)
            val visual = activityVisual(s.name)
            val enough = s.days >= Insights.MIN_DAYS_FOR_PATTERN
            val grow = remember(s.name, v) { Animatable(0f) }
            LaunchedEffect(s.name, v) {
                delay(index * 70L)
                grow.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
            }
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).cartoonSurface(visual.color, colors.outline, CircleShape, 0.dp, 1.5.dp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(visual.icon, null, tint = inkOn(visual.color), modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Text(s.name, style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        if (enough) "n = ${s.days} · ${delta(s).signed()} vs. media" else "n = ${s.days} · pocos datos",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(v.oneDecimal(), style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.widthIn(min = 34.dp))
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .cartoonSurface(colors.surfaceAlt, colors.outline, RoundedCornerShape(50), 0.dp, 2.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(((v / 10.0).toFloat() * grow.value).coerceIn(0.04f, 1f))
                            .cartoonSurface(
                                if (enough) visual.color else visual.color.copy(alpha = 0.4f),
                                colors.outline, RoundedCornerShape(50), 0.dp, 2.dp,
                            )
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Dispersión energía vs. ánimo

/** Desplazamiento pequeño y determinista para que no se encimen los puntos con el mismo valor. */
private fun jitter(seed: Long): Double = (Math.floorMod(seed * 2654435761L, 1000L) / 1000.0 - 0.5) * 0.45

@Composable
private fun ScatterCard(entries: Collection<DayEntry>, rho: Double?) {
    val colors = AnimoTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted)
    CartoonCard(Modifier.fillMaxWidth()) {
        SectionTitle("¿Mi energía y mi ánimo van de la mano?", icon = Icons.Rounded.ScatterPlot, tint = Palette.Teal)
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics { contentDescription = "Dispersión de energía contra ánimo, ${entries.size} días" }
        ) {
            val left = 26.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val top = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            fun x(v: Double) = left + (right - left) * ((v - 1.0) / 9.0).toFloat()
            fun y(v: Double) = bottom - (bottom - top) * ((v - 1.0) / 9.0).toFloat()
            val grid = colors.textMuted.copy(alpha = 0.25f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            listOf(1, 4, 7, 10).forEach { v ->
                val d = v.toDouble()
                drawLine(grid, Offset(left, y(d)), Offset(right, y(d)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                drawLine(grid, Offset(x(d), top), Offset(x(d), bottom), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                val ty = measurer.measure("$v", labelStyle)
                drawText(ty, topLeft = Offset(left - ty.size.width - 6.dp.toPx(), y(d) - ty.size.height / 2))
                val tx = measurer.measure("$v", labelStyle)
                drawText(tx, topLeft = Offset(x(d) - tx.size.width / 2, bottom + 4.dp.toPx()))
            }
            entries.forEach { e ->
                val day = e.date.toEpochDay()
                val c = Offset(x(e.energy + jitter(day)), y(e.mood.score + jitter(day * 31 + 7)))
                drawCircle(e.mood.color, 5.dp.toPx(), c)
                drawCircle(colors.outline, 5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
            }
        }
        Text("Horizontal: energía · Vertical: ánimo", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        Text(
            rho?.let { InsightWriter.correlationSentence(it, entries.size) }
                ?: "Aún no hay datos suficientes: se necesitan al menos ${Insights.MIN_TOTAL_DAYS} días para calcular esta relación.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
        )
    }
}

// ------------------------------------------------------------------ Distribución

@Composable
private fun DistributionCard(journal: Journal) {
    val colors = AnimoTheme.colors
    val dist = Insights.moodDistribution(journal.entries.values)
    val total = dist.values.sum().coerceAtLeast(1)
    CartoonCard(Modifier.fillMaxWidth()) {
        SectionTitle("¿Cómo te has sentido?", icon = Icons.Rounded.PieChart, tint = Palette.Sky)
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            var x = 0f
            val r = CornerRadius(size.height / 2)
            val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
            clipPath(pill) {
                dist.filterValues { it > 0 }.forEach { (mood, n) ->
                    val w = size.width * n / total
                    drawRect(mood.color, Offset(x, 0f), Size(w, size.height))
                    x += w
                }
            }
            drawRoundRect(colors.outline, cornerRadius = r, style = Stroke(2.5.dp.toPx()))
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dist.entries.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { (mood, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoodFace(mood, Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(mood.label, style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f))
                    Text(
                        "$n ${if (n == 1) "día" else "días"} · ${n * 100 / total}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}
