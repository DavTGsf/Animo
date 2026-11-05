package com.example.animo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.animo.data.DayEntry
import com.example.animo.data.MAX_ACTIVITY_NAME
import com.example.animo.data.MAX_ENERGY
import com.example.animo.data.MIN_ENERGY
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette
import com.example.animo.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- Formato

val EsLocale: Locale = Locale.forLanguageTag("es-MX")

private fun String.capitalized() = replaceFirstChar { it.titlecase(EsLocale) }

fun LocalDate.longLabel(): String = format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", EsLocale)).capitalized()
fun LocalDate.dayMonthLabel(): String = format(DateTimeFormatter.ofPattern("d 'de' MMMM", EsLocale))
fun LocalDate.shortLabel(): String = format(DateTimeFormatter.ofPattern("d MMM", EsLocale)).replace(".", "")
fun YearMonth.label(): String = format(DateTimeFormatter.ofPattern("MMMM yyyy", EsLocale)).capitalized()
fun Long.timeLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", EsLocale))
fun Long.dateTimeLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", EsLocale)).replace(".", "")
fun Double.oneDecimal(): String = String.format(EsLocale, "%.1f", this)

fun energyLabel(energy: Int): String = when (energy) {
    in 1..2 -> "Agotado"
    in 3..4 -> "Baja"
    in 5..6 -> "Media"
    in 7..8 -> "Alta"
    else -> "¡A tope!"
}

// ---------------------------------------------------------------- Actividades

data class ActivityVisual(val icon: ImageVector, val color: Color)

private val customColors = listOf(Palette.Lilac, Palette.Teal, Palette.Lime, Palette.Coral, Palette.Sky, Palette.Sun)

fun activityVisual(name: String): ActivityVisual = when (name) {
    "Trabajo" -> ActivityVisual(Icons.Rounded.Work, Palette.Sky)
    "Estudio" -> ActivityVisual(Icons.Rounded.School, Palette.Lilac)
    "Ejercicio" -> ActivityVisual(Icons.Rounded.FitnessCenter, Palette.Mint)
    "Familia" -> ActivityVisual(Icons.Rounded.FamilyRestroom, Palette.Pink)
    "Amigos" -> ActivityVisual(Icons.Rounded.Groups, Palette.Tangerine)
    "Entretenimiento" -> ActivityVisual(Icons.Rounded.SportsEsports, Palette.Grape)
    "Descanso" -> ActivityVisual(Icons.Rounded.Bedtime, Palette.Teal)
    "Viaje" -> ActivityVisual(Icons.Rounded.Flight, Palette.Sun)
    "Compras" -> ActivityVisual(Icons.Rounded.ShoppingBag, Palette.Coral)
    "Tareas domésticas" -> ActivityVisual(Icons.Rounded.CleaningServices, Palette.Lime)
    else -> ActivityVisual(Icons.Rounded.LocalOffer, customColors[Math.floorMod(name.lowercase().hashCode(), customColors.size)])
}

// ---------------------------------------------------------------- Energía

private fun energyColor(level: Int): Color = when {
    level <= 3 -> Palette.Coral
    level <= 6 -> Palette.Sun
    else -> Palette.Mint
}

/** Barra segmentada de energía. Si [onChange] no es null se puede tocar o arrastrar. */
@Composable
fun EnergyBar(
    energy: Int,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 16.dp,
    onChange: ((Int) -> Unit)? = null,
) {
    val colors = AnimoTheme.colors
    val interactive = onChange?.let { change ->
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { pos -> change(((pos.x / size.width) * MAX_ENERGY).toInt().plus(1).coerceIn(MIN_ENERGY, MAX_ENERGY)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { ch, _ ->
                    change(((ch.position.x / size.width) * MAX_ENERGY).toInt().plus(1).coerceIn(MIN_ENERGY, MAX_ENERGY))
                }
            }
            .semantics {
                contentDescription = "Energía"
                progressBarRangeInfo = ProgressBarRangeInfo(energy.toFloat(), MIN_ENERGY.toFloat()..MAX_ENERGY.toFloat(), MAX_ENERGY - 2)
                setProgress { v -> change(v.roundToInt().coerceIn(MIN_ENERGY, MAX_ENERGY)); true }
            }
    } ?: Modifier.semantics { contentDescription = "Energía $energy de $MAX_ENERGY" }

    Row(
        modifier.height(height).then(interactive),
        horizontalArrangement = Arrangement.spacedBy(if (height > 24.dp) 5.dp else 3.dp),
    ) {
        for (i in 1..MAX_ENERGY) {
            val on = i <= energy
            val pop by animateFloatAsState(
                if (i == energy && onChange != null) 1.18f else 1f,
                spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
                label = "seg$i",
            )
            val fill = if (on) energyColor(energy) else colors.surfaceAlt
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { scaleY = pop; scaleX = pop }
                    .cartoonSurface(fill, colors.outline, RoundedCornerShape(if (height > 24.dp) 10.dp else 5.dp), 0.dp, 2.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- Detalle de un día

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDetail(entry: DayEntry, modifier: Modifier = Modifier) {
    val colors = AnimoTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoodFace(entry.mood, Modifier.size(68.dp), alive = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Estado", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Text(entry.mood.label, style = MaterialTheme.typography.headlineSmall, color = colors.text)
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = Palette.Tangerine, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Energía: ${entry.energy}/10", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Spacer(Modifier.width(8.dp))
                Text(energyLabel(entry.energy), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Spacer(Modifier.height(6.dp))
            EnergyBar(entry.energy, Modifier.fillMaxWidth())
        }
        if (entry.activities.isNotEmpty()) {
            Column {
                Text("Actividades", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.activities.forEach { a ->
                        val v = activityVisual(a)
                        Pill(a, v.color, icon = v.icon)
                    }
                }
            }
        }
        Column {
            Text("Diario", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .cartoonSurface(colors.surfaceAlt, colors.outline, RoundedCornerShape(16.dp), 0.dp, 2.dp)
                    .padding(14.dp)
            ) {
                if (entry.reflection.isBlank()) {
                    Text("Sin reflexión este día.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, fontStyle = FontStyle.Italic)
                } else {
                    Text("“${entry.reflection}”", style = MaterialTheme.typography.bodyLarge, color = colors.text)
                }
            }
        }
        Text(
            buildString {
                append("Registrado: ${entry.createdAt.dateTimeLabel()}")
                if (entry.wasEdited) append(" · Editado: ${entry.updatedAt.dateTimeLabel()}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

// ---------------------------------------------------------------- Diálogos

@Composable
fun CartoonDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancelar",
    confirmColor: Color = AnimoTheme.colors.primary,
    icon: ImageVector? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        CartoonCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(56.dp)
                        .cartoonSurface(confirmColor, AnimoTheme.colors.outline, RoundedCornerShape(18.dp), 3.dp)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = inkOn(confirmColor), modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(14.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = AnimoTheme.colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = AnimoTheme.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CartoonButton(onClick = onDismiss, modifier = Modifier.weight(1f), color = AnimoTheme.colors.surface) {
                    Text(dismissLabel)
                }
                CartoonButton(onClick = onConfirm, modifier = Modifier.weight(1f), color = confirmColor) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

/** Diálogo para crear una etiqueta de actividad personalizada. [onAdd] devuelve un error o null. */
@Composable
fun AddActivityDialog(onAdd: (String) -> String?, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val submit: () -> Unit = {
        error = onAdd(name)
        if (error == null) onDismiss()
    }
    Dialog(onDismissRequest = onDismiss) {
        CartoonCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
            Text("Nueva actividad", style = MaterialTheme.typography.titleLarge, color = AnimoTheme.colors.text)
            Spacer(Modifier.height(4.dp))
            Text("Crea una etiqueta propia, por ejemplo “Meditar” o “Leer”.", style = MaterialTheme.typography.bodyMedium, color = AnimoTheme.colors.textMuted)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_ACTIVITY_NAME); error = null },
                singleLine = true,
                isError = error != null,
                label = { Text("Nombre") },
                supportingText = { Text(error ?: "${name.length}/$MAX_ACTIVITY_NAME") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AnimoTheme.colors.primary,
                    unfocusedBorderColor = AnimoTheme.colors.outline,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CartoonButton(onClick = onDismiss, modifier = Modifier.weight(1f), color = AnimoTheme.colors.surface) { Text("Cancelar") }
                CartoonButton(onClick = submit, modifier = Modifier.weight(1f), color = Palette.Mint, enabled = name.isNotBlank()) { Text("Agregar") }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

// ---------------------------------------------------------------- Confeti

private data class Particle(val angle: Float, val speed: Float, val spin: Float, val color: Color, val size: Float, val round: Boolean)

/**
 * Estallido de confeti al guardar un registro: confirma la acción y premia el hábito.
 * Solo se dispara cuando [trigger] cambia después de la primera composición.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    var seen by rememberSaveable { mutableStateOf(trigger) }
    val progress = remember { Animatable(1f) }
    val particles = remember(trigger) {
        val palette = listOf(Palette.Pink, Palette.Sun, Palette.Mint, Palette.Sky, Palette.Grape, Palette.Tangerine)
        List(70) {
            Particle(
                angle = (-160f + Random.nextFloat() * 140f) * (PI / 180f).toFloat(),
                speed = 0.55f + Random.nextFloat() * 0.75f,
                spin = Random.nextFloat() * 720f - 360f,
                color = palette.random(),
                size = 8f + Random.nextFloat() * 10f,
                round = Random.nextBoolean(),
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger != seen) {
            seen = trigger
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1700, easing = LinearEasing))
        }
    }
    if (progress.value < 1f) {
        Canvas(modifier.fillMaxSize()) {
            val t = progress.value
            val origin = Offset(size.width / 2f, size.height * 0.85f)
            val alpha = (1f - t * t * t).coerceIn(0f, 1f)
            particles.forEach { p ->
                val dist = p.speed * size.height * 0.9f
                val x = origin.x + cos(p.angle) * dist * t
                val y = origin.y + sin(p.angle) * dist * t + 1.1f * size.height * t * t
                val pos = Offset(x, y)
                rotate(p.spin * t, pos) {
                    if (p.round) drawCircle(p.color.copy(alpha = alpha), p.size / 2f, pos)
                    else drawRect(p.color.copy(alpha = alpha), Offset(x - p.size / 2f, y - p.size / 4f), Size(p.size, p.size / 2f))
                }
            }
        }
    }
}

/** Mensaje amigable para pantallas vacías, con la mascota. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, mood: com.example.animo.data.Mood = com.example.animo.data.Mood.NEUTRAL) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Bobbing { MoodFace(mood, Modifier.size(84.dp), alive = true) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = AnimoTheme.colors.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = AnimoTheme.colors.textMuted, textAlign = TextAlign.Center)
    }
}

/** Flotación suave para la mascota: da vida sin distraer. */
@Composable
fun Bobbing(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "bobY",
    )
    val rot by t.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(2100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "bobRot",
    )
    Box(modifier.graphicsLayer { translationY = y * density; rotationZ = rot }) { content() }
}

/** Fondo decorativo: burbujas de colores detrás del contenido. */
@Composable
fun BlobBackground(modifier: Modifier = Modifier, tint: Color? = null) {
    val colors = AnimoTheme.colors
    val a = if (colors.isDark) 0.16f else 0.22f
    Canvas(modifier.fillMaxSize()) {
        drawRect(colors.background)
        drawCircle((tint ?: Palette.Sun).copy(alpha = a), size.minDimension * 0.45f, Offset(size.width * 1.02f, size.height * 0.02f))
        drawCircle((tint ?: Palette.Pink).copy(alpha = a * 0.8f), size.minDimension * 0.35f, Offset(-size.width * 0.08f, size.height * 0.42f))
        drawCircle((tint ?: Palette.Sky).copy(alpha = a * 0.8f), size.minDimension * 0.4f, Offset(size.width * 0.95f, size.height * 0.95f))
    }
}
