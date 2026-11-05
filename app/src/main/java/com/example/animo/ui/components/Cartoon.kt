package com.example.animo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.Palette

val CardShape = RoundedCornerShape(24.dp)
val ButtonShape = RoundedCornerShape(18.dp)

/** Relleno + contorno grueso + sombra dura desplazada: la firma visual del estilo cartoon. */
fun Modifier.cartoonSurface(
    fill: Color,
    outline: Color,
    shape: Shape = CardShape,
    shadow: Dp = 4.dp,
    border: Dp = 2.5.dp,
): Modifier = drawBehind {
    val o = shape.createOutline(size, layoutDirection, this)
    val s = shadow.toPx()
    if (s > 0f) translate(s, s) { drawOutline(o, outline) }
    drawOutline(o, fill)
    drawOutline(o, outline, style = Stroke(border.toPx()))
}

/** Color de contenido legible sobre un relleno dado. */
fun inkOn(fill: Color): Color = if (fill.luminance() > 0.42f) Palette.Ink else Color.White

@Composable
fun CartoonCard(
    modifier: Modifier = Modifier,
    color: Color = AnimoTheme.colors.surface,
    shape: Shape = CardShape,
    shadow: Dp = 5.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(end = shadow, bottom = shadow)
            .cartoonSurface(color, AnimoTheme.colors.outline, shape, shadow)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Botón que “se hunde” en su sombra al presionarlo (retroalimentación táctil visual).
 * Altura mínima de 56 dp para cumplir con objetivos táctiles amplios (ley de Fitts).
 */
@Composable
fun CartoonButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AnimoTheme.colors.primary,
    contentColor: Color = inkOn(color),
    enabled: Boolean = true,
    shape: Shape = ButtonShape,
    minHeight: Dp = 56.dp,
    shadow: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = AnimoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessHigh),
        label = "press",
    )
    val fill by animateColorAsState(
        if (enabled) color else color.copy(alpha = 0.35f).compositeOver(colors.surfaceAlt),
        label = "fill",
    )
    val effectiveShadow = if (enabled) shadow else 1.dp
    Row(
        modifier = modifier
            .padding(end = effectiveShadow, bottom = effectiveShadow)
            .defaultMinSize(minHeight = minHeight, minWidth = minHeight)
            .graphicsLayer {
                val offset = effectiveShadow.toPx() * press
                translationX = offset
                translationY = offset
            }
            .cartoonSurface(fill, colors.outline, shape, effectiveShadow * (1f - press))
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = contentColor),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) contentColor else contentColor.copy(alpha = 0.55f),
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content = { content() })
        }
    }
}

/** Botón redondo de icono, 48 dp como mínimo. */
@Composable
fun IconBubble(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AnimoTheme.colors.surface,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    CartoonButton(
        onClick = onClick,
        modifier = modifier,
        color = color,
        shape = CircleShape,
        minHeight = size,
        shadow = 3.dp,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(size * 0.5f))
    }
}

/** Botón con icono y texto, atajo para el caso más común. */
@Composable
fun CartoonIconButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AnimoTheme.colors.primary,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
) {
    CartoonButton(onClick = onClick, modifier = modifier, color = color, enabled = enabled, minHeight = minHeight) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Chip seleccionable. Al activarse “salta” (sombra + color) para que el estado se note de un vistazo.
 */
@Composable
fun CartoonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedColor: Color = AnimoTheme.colors.primary,
) {
    val colors = AnimoTheme.colors
    val fill by animateColorAsState(if (selected) selectedColor else colors.surface, label = "chipFill")
    val shadow by animateDpAsState(
        if (selected) 3.dp else 0.dp,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "chipShadow",
    )
    val scale by animateFloatAsState(
        if (selected) 1f else 0.97f,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow),
        label = "chipScale",
    )
    val content = if (selected) inkOn(selectedColor) else colors.text
    val shape = RoundedCornerShape(50)
    Box(Modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Row(
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .defaultMinSize(minHeight = 48.dp)
                .cartoonSurface(fill, colors.outline, shape, shadow, border = 2.dp)
                .clip(shape)
                .clickable(role = Role.Checkbox, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = content, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Etiqueta pequeña no interactiva. */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val content = inkOn(color)
    Row(
        modifier = modifier
            .cartoonSurface(color, AnimoTheme.colors.outline, RoundedCornerShape(50), 0.dp, 2.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = AnimoTheme.colors.primary) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(
                Modifier
                    .size(32.dp)
                    .cartoonSurface(tint, AnimoTheme.colors.outline, CircleShape, 0.dp, 2.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = inkOn(tint), modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge, color = AnimoTheme.colors.text)
    }
}
