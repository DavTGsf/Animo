package com.example.animo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.animo.data.Mood

/** Paleta llamativa estilo caricatura. */
object Palette {
    val Ink = Color(0xFF1E1B2E)
    val Cream = Color(0xFFFFF7E8)
    val Grape = Color(0xFF7C4DFF)
    val Pink = Color(0xFFFF5CAA)
    val Sun = Color(0xFFFFD23F)
    val Mint = Color(0xFF3DDC97)
    val Sky = Color(0xFF4DC3FF)
    val Tangerine = Color(0xFFFF9F1C)
    val Coral = Color(0xFFFF6B6B)
    val Lilac = Color(0xFFB69CFF)
    val Lime = Color(0xFFB8F35A)
    val Teal = Color(0xFF2EC4B6)
    val White = Color(0xFFFFFFFF)
}

val Mood.color: Color
    get() = when (this) {
        Mood.ENOJADO -> Color(0xFFFF4D4D)
        Mood.TRISTE -> Color(0xFF4D8BFF)
        Mood.DEPRIMIDO -> Color(0xFF9D8DF1)
        Mood.PREOCUPADO -> Color(0xFFFF9F1C)
        Mood.NEUTRAL -> Color(0xFFBFC6D1)
        Mood.BIEN -> Color(0xFF7ED957)
        Mood.FELIZ -> Color(0xFFFFD23F)
        Mood.EXCELENTE -> Color(0xFFFF5CAA)
    }

@Immutable
data class AnimoColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    /** Contorno grueso y sombra dura de los elementos cartoon. */
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val danger: Color,
    val isDark: Boolean,
)

private val LightAnimo = AnimoColors(
    background = Palette.Cream,
    surface = Palette.White,
    surfaceAlt = Color(0xFFFFEFD1),
    outline = Palette.Ink,
    text = Palette.Ink,
    textMuted = Color(0xFF5E5A73),
    primary = Palette.Grape,
    onPrimary = Palette.White,
    accent = Palette.Pink,
    danger = Color(0xFFE5383B),
    isDark = false,
)

private val DarkAnimo = AnimoColors(
    background = Color(0xFF17142A),
    surface = Color(0xFF2A2550),
    surfaceAlt = Color(0xFF342E63),
    outline = Color(0xFF07060F),
    text = Palette.Cream,
    textMuted = Color(0xFFBDB6DD),
    primary = Color(0xFF9E7BFF),
    onPrimary = Palette.Ink,
    accent = Palette.Pink,
    danger = Color(0xFFFF6B6B),
    isDark = true,
)

val LocalAnimoColors = staticCompositionLocalOf { LightAnimo }

private val Rounded = FontFamily.SansSerif

private val AnimoTypography = Typography(
    displaySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun AnimoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkAnimo else LightAnimo
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, secondary = c.accent,
            background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.textMuted, outline = c.outline, error = c.danger,
            surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceAlt,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, secondary = c.accent,
            background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.textMuted, outline = c.outline, error = c.danger,
            surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceAlt,
        )
    }
    CompositionLocalProvider(LocalAnimoColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AnimoTypography, content = content)
    }
}

object AnimoTheme {
    val colors: AnimoColors
        @Composable get() = LocalAnimoColors.current
}
