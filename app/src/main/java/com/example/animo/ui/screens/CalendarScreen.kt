package com.example.animo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.animo.data.DayEntry
import com.example.animo.data.Journal
import com.example.animo.data.Mood
import com.example.animo.domain.Insights
import com.example.animo.ui.components.CartoonButton
import com.example.animo.ui.components.CartoonCard
import com.example.animo.ui.components.CartoonDialog
import com.example.animo.ui.components.CartoonIconButton
import com.example.animo.ui.components.EmptyState
import com.example.animo.ui.components.EntryDetail
import com.example.animo.ui.components.EsLocale
import com.example.animo.ui.components.IconBubble
import com.example.animo.ui.components.cartoonSurface
import com.example.animo.ui.components.dayMonthLabel
import com.example.animo.ui.components.inkOn
import com.example.animo.ui.components.label
import com.example.animo.ui.components.oneDecimal
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette
import com.example.animo.ui.theme.color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
fun CalendarScreen(
    journal: Journal,
    today: LocalDate,
    allowPastDays: Boolean,
    contentPadding: PaddingValues,
    onCreateOrEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
) {
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var selected by rememberSaveable { mutableStateOf(today) }
    var confirmDelete by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val calendar: @Composable (Modifier) -> Unit = { m ->
            MonthCard(
                month = month,
                today = today,
                selected = selected,
                entries = journal.entries,
                onMonthChange = { month = it },
                onSelect = { selected = it },
                modifier = m,
            )
        }
        val detail: @Composable (Modifier) -> Unit = { m ->
            DayDetailCard(
                date = selected,
                entry = journal.entries[selected],
                canCreate = selected == today || (selected.isBefore(today) && allowPastDays),
                isFuture = selected.isAfter(today),
                onCreateOrEdit = { onCreateOrEdit(selected) },
                onDelete = { confirmDelete = selected },
                modifier = m,
            )
        }
        if (wide) {
            // Tablet / horizontal: calendario y detalle lado a lado.
            Row(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                calendar(Modifier.weight(1.1f))
                detail(Modifier.weight(1f))
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                calendar(Modifier.fillMaxWidth())
                detail(Modifier.fillMaxWidth())
            }
        }
    }

    confirmDelete?.let { date ->
        CartoonDialog(
            title = "¿Eliminar el ${date.dayMonthLabel()}?",
            message = "Se borrará el registro completo de ese día. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            confirmColor = AnimoTheme.colors.danger,
            icon = Icons.Rounded.DeleteOutline,
            onConfirm = { onDelete(date); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthCard(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    entries: Map<LocalDate, DayEntry>,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val colors = AnimoTheme.colors
    val thisMonth = YearMonth.from(today)
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    CartoonCard(modifier, contentPadding = PaddingValues(16.dp)) {
        Text("Tu calendario", style = MaterialTheme.typography.headlineSmall, color = colors.text)
        Spacer(Modifier.height(12.dp))
        // Navegación entre meses: botones grandes en los extremos y gesto de deslizar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mes anterior", { onMonthChange(month.minusMonths(1)) })
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { dir * it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -dir * it / 2 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "monthLabel",
            ) { m ->
                Text(
                    m.label(),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            IconBubble(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                "Mes siguiente",
                { onMonthChange(month.plusMonths(1)) },
                enabled = month < thisMonth,
            )
        }
        if (month != thisMonth) {
            Spacer(Modifier.height(8.dp))
            CartoonButton(
                onClick = { onMonthChange(thisMonth); onSelect(today) },
                color = Palette.Sun,
                minHeight = 40.dp,
                shadow = 3.dp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Ir a hoy", style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(Modifier.height(14.dp))

        // Encabezado de días (lunes primero)
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.SHORT, EsLocale).take(2).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { dir * it } + fadeIn()) togetherWith (slideOutHorizontally { -dir * it } + fadeOut())
            },
            modifier = Modifier.pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        when {
                            dragTotal > swipeThreshold -> onMonthChange(month.minusMonths(1))
                            dragTotal < -swipeThreshold && month < thisMonth -> onMonthChange(month.plusMonths(1))
                        }
                    },
                ) { _, amount -> dragTotal += amount }
            },
            label = "monthGrid",
        ) { m ->
            MonthGrid(m, today, selected, entries, onSelect)
        }

        // Resumen del mes
        val monthEntries = entries.values.filter { YearMonth.from(it.date) == month }
        Spacer(Modifier.height(12.dp))
        Text(
            if (monthEntries.isEmpty()) "Sin registros este mes"
            else "${monthEntries.size} ${if (monthEntries.size == 1) "día registrado" else "días registrados"} · ánimo promedio ${Insights.averageMood(monthEntries)!!.oneDecimal()}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        // Leyenda de colores
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Mood.entries.forEach { mood ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).cartoonSurface(mood.color, colors.outline, CircleShape, 0.dp, 1.5.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(mood.label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    entries: Map<LocalDate, DayEntry>,
    onSelect: (LocalDate) -> Unit,
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = offset + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            DayCell(
                                date = date,
                                entry = entries[date],
                                isToday = date == today,
                                isSelected = date == selected,
                                isFuture = date.isAfter(today),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    entry: DayEntry?,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
) {
    val colors = AnimoTheme.colors
    val scale by animateFloatAsState(
        if (isSelected) 1.12f else 1f,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
        label = "dayScale",
    )
    val shape = RoundedCornerShape(12.dp)
    val fill = entry?.mood?.color ?: colors.surface
    val outline = if (entry != null || isToday || isSelected) colors.outline else colors.textMuted.copy(alpha = 0.35f)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (isFuture) 0.35f else 1f }
            .cartoonSurface(
                fill,
                outline,
                shape,
                shadow = if (isSelected) 3.dp else 0.dp,
                border = if (isToday || isSelected) 3.dp else 1.5.dp,
            )
            .clip(shape)
            .clickable(enabled = !isFuture, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append(date.dayMonthLabel())
                    if (isToday) append(", hoy")
                    append(entry?.let { ", ${it.mood.label}" } ?: ", sin registro")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold,
            color = if (entry != null) inkOn(fill) else colors.text,
        )
    }
}

@Composable
private fun DayDetailCard(
    date: LocalDate,
    entry: DayEntry?,
    canCreate: Boolean,
    isFuture: Boolean,
    onCreateOrEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = date to entry,
        contentKey = { it.first },
        transitionSpec = {
            (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut()
        },
        modifier = modifier,
        label = "dayDetail",
    ) { (d, e) ->
        CartoonCard(Modifier.fillMaxWidth()) {
            Text(
                d.dayMonthLabel().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineSmall,
                color = AnimoTheme.colors.text,
            )
            Text(
                d.dayOfWeek.getDisplayName(TextStyle.FULL, EsLocale).replaceFirstChar { it.uppercase() } + " · " + d.year,
                style = MaterialTheme.typography.bodySmall,
                color = AnimoTheme.colors.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            if (e != null) {
                EntryDetail(e)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CartoonIconButton("Editar", Icons.Rounded.Edit, onCreateOrEdit, Modifier.weight(1f))
                    IconBubble(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Eliminar registro",
                        onClick = onDelete,
                        color = AnimoTheme.colors.surfaceAlt,
                        size = 56.dp,
                    )
                }
            } else {
                EmptyState(
                    title = if (isFuture) "Aún no llega este día" else "Sin registro",
                    message = when {
                        isFuture -> "Vuelve ese día para contarnos cómo te fue."
                        canCreate -> "No registraste nada este día. ¿Quieres hacerlo ahora?"
                        else -> "Solo puedes registrar el día de hoy. Activa “Registrar días anteriores” en Ajustes si lo necesitas."
                    },
                    mood = Mood.NEUTRAL,
                )
                if (canCreate) {
                    Spacer(Modifier.height(12.dp))
                    CartoonIconButton("Registrar este día", Icons.Rounded.Add, onCreateOrEdit, Modifier.fillMaxWidth(), color = Palette.Pink)
                } else if (!isFuture) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.EventBusy, null, tint = AnimoTheme.colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
