package com.example.animo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.animo.data.MAX_ENERGY
import com.example.animo.data.MIN_ENERGY
import com.example.animo.data.Mood
import com.example.animo.ui.EditorDraft
import com.example.animo.ui.components.AddActivityDialog
import com.example.animo.ui.components.CartoonButton
import com.example.animo.ui.components.CartoonCard
import com.example.animo.ui.components.CartoonChip
import com.example.animo.ui.components.CartoonDialog
import com.example.animo.ui.components.EnergyBar
import com.example.animo.ui.components.IconBubble
import com.example.animo.ui.components.MoodFace
import com.example.animo.ui.components.SectionTitle
import com.example.animo.ui.components.activityVisual
import com.example.animo.ui.components.cartoonSurface
import com.example.animo.ui.components.energyLabel
import com.example.animo.ui.components.longLabel
import com.example.animo.ui.components.timeLabel
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette
import com.example.animo.ui.theme.color

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    draft: EditorDraft,
    activities: List<String>,
    maxChars: Int,
    onMood: (Mood) -> Unit,
    onEnergy: (Int) -> Unit,
    onToggleActivity: (String) -> Unit,
    onAddActivity: (String) -> String?,
    onReflection: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = AnimoTheme.colors
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val moodColor = draft.mood?.color ?: colors.primary
    // El fondo se tiñe con el color del ánimo elegido: refuerzo visual inmediato de la selección.
    val tint by animateColorAsState(
        draft.mood?.color?.copy(alpha = if (colors.isDark) 0.18f else 0.28f)?.compositeOver(colors.background) ?: colors.background,
        tween(450),
        label = "editorTint",
    )
    val dirty = draft.existing?.let {
        it.mood != draft.mood || it.energy != draft.energy || it.activities.toSet() != draft.activities || it.reflection != draft.reflection
    } ?: (draft.mood != null || draft.activities.isNotEmpty() || draft.reflection.isNotBlank())
    val requestClose: () -> Unit = {
        if (dirty) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }

    // Asegura que las actividades de registros viejos (etiquetas borradas) sigan visibles.
    val allActivities = activities + draft.activities.filter { it !in activities }

    Box(Modifier.fillMaxSize().background(tint)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // ---------- Barra superior
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBubble(Icons.Rounded.Close, "Cerrar", onClick = requestClose)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (draft.existing != null) "Editar registro" else "Nuevo registro",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${draft.date.longLabel()} · ${(draft.existing?.createdAt ?: draft.openedAt).timeLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            // ---------- Contenido
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val maxW = Modifier.widthIn(max = 720.dp).fillMaxWidth()

                // 1. Estado de ánimo
                CartoonCard(maxW) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionTitle("¿Cómo te sientes?", icon = Icons.Rounded.Mood, tint = Palette.Pink)
                            AnimatedContent(
                                targetState = draft.mood,
                                transitionSpec = {
                                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                                },
                                label = "moodLabel",
                            ) { m ->
                                Text(
                                    m?.label ?: "Elige una carita",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (m == null) colors.textMuted else colors.text,
                                    modifier = Modifier.padding(start = 42.dp),
                                )
                            }
                        }
                        // La carita grande se transforma en vivo al cambiar de ánimo.
                        MoodFace(draft.mood ?: Mood.NEUTRAL, Modifier.size(72.dp), alive = true, fill = if (draft.mood == null) colors.surfaceAlt else moodColor)
                    }
                    Spacer(Modifier.height(16.dp))
                    MoodGrid(selected = draft.mood, onSelect = onMood)
                }

                // 2. Energía
                CartoonCard(maxW) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Energía", icon = Icons.Rounded.Bolt, tint = Palette.Tangerine, modifier = Modifier.weight(1f))
                        Text("${draft.energy}/10", style = MaterialTheme.typography.headlineSmall, color = colors.text)
                    }
                    Text(
                        energyLabel(draft.energy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(start = 42.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Remove, "Menos energía", { onEnergy(draft.energy - 1) }, enabled = draft.energy > MIN_ENERGY)
                        Spacer(Modifier.width(10.dp))
                        EnergyBar(draft.energy, Modifier.weight(1f), height = 44.dp, onChange = onEnergy)
                        Spacer(Modifier.width(10.dp))
                        IconBubble(Icons.Rounded.Add, "Más energía", { onEnergy(draft.energy + 1) }, enabled = draft.energy < MAX_ENERGY)
                    }
                }

                // 3. Actividades
                CartoonCard(maxW) {
                    SectionTitle("¿Qué hiciste hoy?", icon = Icons.Rounded.Interests, tint = Palette.Mint)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        allActivities.forEach { name ->
                            val v = activityVisual(name)
                            CartoonChip(
                                text = name,
                                selected = name in draft.activities,
                                onClick = { onToggleActivity(name) },
                                icon = v.icon,
                                selectedColor = v.color,
                            )
                        }
                        CartoonChip(
                            text = "Agregar actividad",
                            selected = false,
                            onClick = { showAdd = true },
                            icon = Icons.Rounded.Add,
                        )
                    }
                }

                // 4. Reflexión
                CartoonCard(maxW) {
                    SectionTitle("Reflexión", icon = Icons.Rounded.EditNote, tint = Palette.Sky)
                    Text(
                        "Opcional · ¿Qué pasó hoy? ¿Qué te hizo sentir así?",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(start = 42.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = draft.reflection,
                        onValueChange = onReflection,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 130.dp)
                                    .cartoonSurface(colors.surfaceAlt, colors.outline, RoundedCornerShape(16.dp), 0.dp, 2.dp)
                                    .padding(14.dp)
                            ) {
                                if (draft.reflection.isEmpty()) {
                                    Text(
                                        "Hoy tuve mucho trabajo, pero terminé el proyecto que tenía pendiente. Por la tarde salí a caminar y me sentí mucho mejor…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textMuted.copy(alpha = 0.7f),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    val used = draft.reflection.length
                    val counterColor = when {
                        used >= maxChars -> colors.danger
                        used >= maxChars * 0.9 -> Palette.Tangerine
                        else -> colors.textMuted
                    }
                    Text(
                        "$used/$maxChars",
                        style = MaterialTheme.typography.labelMedium,
                        color = counterColor,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // ---------- Acción principal anclada abajo (zona del pulgar, ancho completo)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background.copy(alpha = 0.92f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (draft.mood == null) {
                    Text(
                        "Elige cómo te sientes para poder guardar",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                CartoonButton(
                    onClick = onSave,
                    enabled = draft.mood != null,
                    color = moodColor,
                    minHeight = 64.dp,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (draft.existing != null) "Guardar cambios" else "Guardar mi día")
                }
            }
        }
    }

    if (showAdd) {
        AddActivityDialog(onAdd = onAddActivity, onDismiss = { showAdd = false })
    }
    if (confirmDiscard) {
        CartoonDialog(
            title = "¿Descartar cambios?",
            message = "Lo que escribiste en este registro no se guardará.",
            confirmLabel = "Descartar",
            confirmColor = colors.danger,
            onConfirm = { confirmDiscard = false; onClose() },
            onDismiss = { confirmDiscard = false },
            dismissLabel = "Seguir editando",
        )
    }
    androidx.activity.compose.BackHandler(onBack = requestClose)
}
