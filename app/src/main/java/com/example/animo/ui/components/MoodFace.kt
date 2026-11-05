package com.example.animo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.animo.data.Mood
import com.example.animo.ui.theme.AnimoTheme
import com.example.animo.ui.theme.color

/** Rasgos de cada carita. Todos los valores numéricos se animan al cambiar de estado. */
private data class FaceSpec(
    val mouthCurve: Float,       // -1 = puchero, 1 = gran sonrisa
    val mouthOpen: Float = 0f,   // 1 = boca abierta rellena
    val wavy: Boolean = false,   // boca temblorosa (preocupación)
    val browTilt: Float = 0f,    // + = cejas de enojo, - = cejas tristes
    val browAlpha: Float = 0f,
    val eyes: Eyes = Eyes.DOT,
    val blush: Float = 0f,
    val tear: Float = 0f,
    val sweat: Float = 0f,
)

private enum class Eyes { DOT, SLEEPY, HAPPY }

private fun Mood.face(): FaceSpec = when (this) {
    Mood.ENOJADO -> FaceSpec(mouthCurve = -0.55f, browTilt = 1f, browAlpha = 1f)
    Mood.TRISTE -> FaceSpec(mouthCurve = -0.85f, browTilt = -0.8f, browAlpha = 1f, tear = 1f)
    Mood.DEPRIMIDO -> FaceSpec(mouthCurve = -0.35f, eyes = Eyes.SLEEPY, browTilt = -0.4f, browAlpha = 0.8f)
    Mood.PREOCUPADO -> FaceSpec(mouthCurve = 0f, wavy = true, browTilt = -1f, browAlpha = 1f, sweat = 1f)
    Mood.NEUTRAL -> FaceSpec(mouthCurve = 0f)
    Mood.BIEN -> FaceSpec(mouthCurve = 0.45f, blush = 0.5f)
    Mood.FELIZ -> FaceSpec(mouthCurve = 0.9f, blush = 1f)
    Mood.EXCELENTE -> FaceSpec(mouthCurve = 1f, mouthOpen = 1f, eyes = Eyes.HAPPY, blush = 1f)
}

/**
 * Carita cartoon dibujada a mano en Canvas. Cuando cambia el [mood] la expresión se
 * transforma suavemente (la boca se curva, aparecen cejas o lágrimas), reforzando la elección.
 * Con [alive] parpadea de vez en cuando para dar personalidad.
 */
@Composable
fun MoodFace(
    mood: Mood,
    modifier: Modifier = Modifier,
    alive: Boolean = false,
    outline: Color = AnimoTheme.colors.outline,
    fill: Color = mood.color,
) {
    val spec = mood.face()
    val bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
    val mouth by animateFloatAsState(spec.mouthCurve, bouncy, label = "mouth")
    val open by animateFloatAsState(spec.mouthOpen, label = "open")
    val brow by animateFloatAsState(spec.browTilt, bouncy, label = "brow")
    val browAlpha by animateFloatAsState(spec.browAlpha, label = "browAlpha")
    val blush by animateFloatAsState(spec.blush, label = "blush")
    val tear by animateFloatAsState(spec.tear, label = "tear")
    val sweat by animateFloatAsState(spec.sweat, label = "sweat")
    val faceColor by animateColorAsState(fill, label = "faceColor")

    val blink = if (alive) {
        val t = rememberInfiniteTransition(label = "blink")
        val v by t.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 3800
                    1f at 3500
                    0.1f at 3600
                    1f at 3750
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "blinkValue",
        )
        v
    } else 1f

    Canvas(modifier.semantics { contentDescription = mood.label }) {
        val r = size.minDimension / 2f
        val c = center
        val stroke = (r * 0.09f).coerceAtLeast(2f)
        val faceR = r - stroke / 2f

        // Cara
        drawCircle(faceColor, faceR, c)
        drawCircle(outline, faceR, c, style = Stroke(stroke))

        // Brillo cartoon
        drawCircle(Color.White.copy(alpha = 0.45f), faceR * 0.16f, Offset(c.x - faceR * 0.5f, c.y - faceR * 0.52f))

        // Chapitas
        if (blush > 0f) {
            val blushColor = Color(0xFFFF7EB6).copy(alpha = 0.7f * blush)
            val bs = Size(faceR * 0.34f, faceR * 0.18f)
            drawOval(blushColor, Offset(c.x - faceR * 0.72f, c.y + faceR * 0.12f), bs)
            drawOval(blushColor, Offset(c.x + faceR * 0.38f, c.y + faceR * 0.12f), bs)
        }

        // Ojos
        val eyeDx = faceR * 0.36f
        val eyeY = c.y - faceR * 0.12f
        val eyeR = faceR * 0.11f
        for (side in listOf(-1f, 1f)) {
            val e = Offset(c.x + side * eyeDx, eyeY)
            when (spec.eyes) {
                Eyes.DOT -> scale(1f, blink, pivot = e) { drawCircle(outline, eyeR, e) }
                Eyes.SLEEPY -> drawLine(
                    outline, Offset(e.x - eyeR * 1.3f, e.y + eyeR * 0.4f), Offset(e.x + eyeR * 1.3f, e.y + eyeR * 0.4f),
                    strokeWidth = stroke * 0.9f, cap = StrokeCap.Round,
                )
                Eyes.HAPPY -> {
                    val p = Path().apply {
                        moveTo(e.x - eyeR * 1.3f, e.y + eyeR * 0.5f)
                        quadraticTo(e.x, e.y - eyeR * 1.8f, e.x + eyeR * 1.3f, e.y + eyeR * 0.5f)
                    }
                    drawPath(p, outline, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
                }
            }
            // Cejas
            if (browAlpha > 0f) {
                val bw = eyeR * 1.5f
                val by = eyeY - faceR * 0.28f
                val t = brow * faceR * 0.09f
                // El extremo interior (hacia el centro) baja con enojo y sube con tristeza.
                val outer = Offset(e.x + side * bw, by - t)
                val inner = Offset(e.x - side * bw, by + t)
                drawLine(outline.copy(alpha = browAlpha), outer, inner, strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
            }
        }

        drawMouth(c, faceR, stroke, outline, mouth, open, spec.wavy)

        // Lágrima
        if (tear > 0f) {
            val tx = c.x - eyeDx - eyeR * 0.2f
            val ty = eyeY + eyeR * 1.6f + (1f - tear) * faceR * 0.2f
            val drop = Path().apply {
                moveTo(tx, ty)
                quadraticTo(tx + eyeR * 1.2f, ty + eyeR * 1.6f, tx, ty + eyeR * 2.1f)
                quadraticTo(tx - eyeR * 1.2f, ty + eyeR * 1.6f, tx, ty)
                close()
            }
            drawPath(drop, Color(0xFF7FD6FF).copy(alpha = tear))
            drawPath(drop, outline.copy(alpha = tear), style = Stroke(stroke * 0.5f))
        }

        // Gota de sudor
        if (sweat > 0f) {
            val sx = c.x + faceR * 0.7f
            val sy = c.y - faceR * 0.62f
            val drop = Path().apply {
                moveTo(sx, sy)
                quadraticTo(sx + eyeR * 1.3f, sy + eyeR * 1.8f, sx, sy + eyeR * 2.3f)
                quadraticTo(sx - eyeR * 1.3f, sy + eyeR * 1.8f, sx, sy)
                close()
            }
            drawPath(drop, Color(0xFFBDEBFF).copy(alpha = sweat))
            drawPath(drop, outline.copy(alpha = sweat), style = Stroke(stroke * 0.5f))
        }
    }
}

private fun DrawScope.drawMouth(c: Offset, r: Float, stroke: Float, ink: Color, curve: Float, open: Float, wavy: Boolean) {
    val halfW = r * (0.34f + 0.08f * curve.coerceAtLeast(0f))
    val baseY = c.y + r * 0.32f + (-curve).coerceAtLeast(0f) * r * 0.14f
    val left = c.x - halfW
    val right = c.x + halfW
    if (wavy) {
        val p = Path().apply {
            moveTo(left, baseY)
            val step = (right - left) / 4f
            for (i in 0 until 4) {
                val x0 = left + step * i
                val dir = if (i % 2 == 0) -1f else 1f
                quadraticTo(x0 + step / 2f, baseY + dir * r * 0.1f, x0 + step, baseY)
            }
        }
        drawPath(p, ink, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
        return
    }
    val control = Offset(c.x, baseY + curve * r * 0.5f)
    val p = Path().apply {
        moveTo(left, baseY)
        quadraticTo(control.x, control.y, right, baseY)
    }
    if (open > 0f) {
        val filled = Path().apply {
            addPath(p)
            close()
        }
        drawPath(filled, ink.copy(alpha = open))
        // Lengüita (recortada a la forma de la boca)
        clipPath(filled) {
            drawCircle(
                Color(0xFFFF6B8B).copy(alpha = open),
                radius = r * 0.16f,
                center = Offset(c.x, baseY + curve * r * 0.3f),
            )
        }
    }
    drawPath(p, ink, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
}
