package com.example.animo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.example.animo.data.DayEntry
import com.example.animo.data.Journal
import com.example.animo.data.Mood
import com.example.animo.domain.Insights
import com.example.animo.ui.components.Bobbing
import com.example.animo.ui.components.CartoonCard
import com.example.animo.ui.components.CartoonDialog
import com.example.animo.ui.components.CartoonIconButton
import com.example.animo.ui.components.EntryDetail
import com.example.animo.ui.components.MoodFace
import com.example.animo.ui.components.Pill
import com.example.animo.ui.components.SectionTitle
import com.example.animo.ui.components.cartoonSurface
import com.example.animo.ui.components.longLabel
import com.example.animo.ui.components.oneDecimal
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette
import com.example.animo.ui.theme.color
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import com.example.animo.ui.components.EsLocale

@Composable
fun TodayScreen(
    journal: Journal,
    today: LocalDate,
    contentPadding: PaddingValues,
    onPickMood: (Mood) -> Unit,
    onEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onSeeInsights: () -> Unit,
) {
    val entry = journal.entries[today]
    val streak = Insights.streak(journal.entries, today)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") { Header(today, streak, entry?.mood) }

        item(key = "today") {
            AnimatedContent(
                targetState = entry,
                contentKey = { it == null },
                transitionSpec = {
                    (scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = 0.6f)) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.9f) + fadeOut())
                },
                label = "todayCard",
            ) { e ->
                if (e == null) {
                    MoodPickerCard(onPickMood)
                } else {
                    TodayEntryCard(e, onEdit = { onEdit(today) }, onDelete = { confirmDelete = true })
                }
            }
        }

        item(key = "week") { WeekStrip(journal, today) }

        item(key = "tip") { InsightTeaser(journal, onSeeInsights) }
    }

    if (confirmDelete) {
        CartoonDialog(
            title = "¿Eliminar el registro de hoy?",
            message = "Se borrarán tu estado, energía, actividades y reflexión. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            confirmColor = AnimoTheme.colors.danger,
            icon = Icons.Rounded.DeleteOutline,
            onConfirm = { onDelete(today); confirmDelete = false },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Header(today: LocalDate, streak: Int, mood: Mood?) {
    val colors = AnimoTheme.colors
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "¡Buenos días!"
        in 12..18 -> "¡Buenas tardes!"
        else -> "¡Buenas noches!"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.displaySmall, color = colors.text)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Today, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(today.longLabel(), style = MaterialTheme.typography.titleSmall, color = colors.textMuted)
            }
            if (streak > 0) {
                Spacer(Modifier.height(10.dp))
                Pill(
                    text = if (streak == 1) "1 día registrado" else "$streak días seguidos",
                    color = Palette.Tangerine,
                    icon = Icons.Rounded.LocalFireDepartment,
                )
            }
        }
        // La mascota refleja cómo te sientes hoy.
        Bobbing { MoodFace(mood ?: Mood.NEUTRAL, Modifier.size(78.dp), alive = true) }
    }
}

@Composable
private fun MoodPickerCard(onPickMood: (Mood) -> Unit) {
    val colors = AnimoTheme.colors
    CartoonCard(Modifier.fillMaxWidth(), color = colors.surface) {
        Text("¿Cómo te sientes hoy?", style = MaterialTheme.typography.headlineSmall, color = colors.text)
        Spacer(Modifier.height(4.dp))
        Text("Toca una carita para empezar tu registro", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        Spacer(Modifier.height(16.dp))
        MoodGrid(selected = null, onSelect = onPickMood)
    }
}

/**
 * Cuadrícula de estados de ánimo. 4 columnas en teléfono (celdas ≥ 72 dp) y 8 en pantallas anchas.
 */
@Composable
fun MoodGrid(selected: Mood?, onSelect: (Mood) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 560.dp) 8 else 4
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Mood.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { mood ->
                        MoodCell(mood, selected == mood, selected != null && selected != mood, { onSelect(mood) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodCell(mood: Mood, isSelected: Boolean, dimmed: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = AnimoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.9f
            isSelected -> 1.08f
            dimmed -> 0.92f
            else -> 1f
        },
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
        label = "moodScale",
    )
    val alpha by animateFloatAsState(if (dimmed) 0.55f else 1f, label = "moodAlpha")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .then(
                if (isSelected) Modifier.cartoonSurface(mood.color.copy(alpha = 0.35f), colors.outline, shape, 3.dp, 2.dp)
                else Modifier
            )
            .clip(shape)
            .clickable(interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoodFace(mood, Modifier.fillMaxWidth(0.82f).widthIn(max = 64.dp).aspectRatio(1f), alive = isSelected)
        Spacer(Modifier.height(6.dp))
        Text(
            mood.label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun TodayEntryCard(entry: DayEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    CartoonCard(Modifier.fillMaxWidth(), color = AnimoTheme.colors.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tu día de hoy", style = MaterialTheme.typography.titleLarge, color = AnimoTheme.colors.text, modifier = Modifier.weight(1f))
            Pill("Guardado", Palette.Mint)
        }
        Spacer(Modifier.height(14.dp))
        EntryDetail(entry)
        Spacer(Modifier.height(18.dp))
        // Acción principal grande; la destructiva más pequeña y separada (Fitts + prevención de errores).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CartoonIconButton("Editar", Icons.Rounded.Edit, onEdit, Modifier.weight(1f))
            com.example.animo.ui.components.IconBubble(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Eliminar registro",
                onClick = onDelete,
                color = AnimoTheme.colors.surfaceAlt,
                size = 56.dp,
            )
        }
    }
}

@Composable
private fun WeekStrip(journal: Journal, today: LocalDate) {
    val colors = AnimoTheme.colors
    CartoonCard(Modifier.fillMaxWidth(), color = colors.surface, contentPadding = PaddingValues(16.dp)) {
        SectionTitle("Tu semana", tint = Palette.Sky)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (6 downTo 0).forEach { back ->
                val day = today.minusDays(back.toLong())
                val e = journal.entries[day]
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.NARROW, EsLocale).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (day == today) colors.primary else colors.textMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (e != null) {
                        MoodFace(e.mood, Modifier.size(34.dp))
                    } else {
                        val outline = colors.textMuted
                        Canvas(Modifier.size(34.dp)) {
                            drawCircle(
                                outline.copy(alpha = 0.6f),
                                radius = size.minDimension / 2f - 2.dp.toPx(),
                                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun InsightTeaser(journal: Journal, onSeeInsights: () -> Unit) {
    val colors = AnimoTheme.colors
    val best = Insights.activityStats(journal.entries.values)
        .filter { it.days >= Insights.MIN_DAYS_FOR_PATTERN && it.moodDelta > 0.2 }
        .maxByOrNull { it.moodDelta }
    CartoonCard(Modifier.fillMaxWidth(), color = colors.surfaceAlt) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .cartoonSurface(Palette.Grape, colors.outline, CircleShape, 2.dp, 2.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.White, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(
                if (best != null) "Los días con ${best.name} tu ánimo está ${best.moodDelta.oneDecimal()} puntos arriba de tu promedio."
                else "Registra varios días seguidos y descubriremos qué actividades te hacen sentir mejor.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        CartoonIconButton(
            "Ver mis patrones",
            Icons.Rounded.AutoAwesome,
            onSeeInsights,
            Modifier.fillMaxWidth(),
            color = Palette.Sun,
            minHeight = 52.dp,
        )
    }
}
