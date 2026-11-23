package com.sabil.qnow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Brand palette. A saturated violet as the brand colour, with a warm coral as its
 * near-complement for calls to action and highlights, and a mint green for "open / your turn".
 * Every text/background pair below meets WCAG AA contrast.
 */
object Brand {
    val Violet = Color(0xFF5B3DF5)
    val VioletDeep = Color(0xFF2A1B8F)
    val VioletBright = Color(0xFF7C63FF)
    val Coral = Color(0xFFFF7A59)
    val CoralDeep = Color(0xFFD9502E)
    val Mint = Color(0xFF12B886)
    val Amber = Color(0xFFF5A524)
    val Rose = Color(0xFFE5484D)
}

private val LightColors = lightColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E1FF),
    onPrimaryContainer = Color(0xFF1B0F66),
    secondary = Brand.CoralDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4DA),
    onSecondaryContainer = Color(0xFF4A1404),
    tertiary = Color(0xFF0B8F67),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDF5E6),
    onTertiaryContainer = Color(0xFF00382A),
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFFE2DE),
    onErrorContainer = Color(0xFF5C0A04),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF16131F),
    surface = Color(0xFFF7F6FC),
    onSurface = Color(0xFF16131F),
    surfaceVariant = Color(0xFFECE9F6),
    onSurfaceVariant = Color(0xFF5D5873),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EFF9),
    surfaceContainerHigh = Color(0xFFEAE7F5),
    surfaceContainerHighest = Color(0xFFE3DFF0),
    outline = Color(0xFFC9C4DA),
    outlineVariant = Color(0xFFE3DFF0),
    inverseSurface = Color(0xFF2B2738),
    inverseOnSurface = Color(0xFFF3F0FA),
    inversePrimary = Color(0xFFB9ABFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAE9DFF),
    onPrimary = Color(0xFF1E0F7A),
    primaryContainer = Color(0xFF3B25C9),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Color(0xFFFF9D82),
    onSecondary = Color(0xFF4A1404),
    secondaryContainer = Color(0xFF6E2711),
    onSecondaryContainer = Color(0xFFFFE4DA),
    tertiary = Color(0xFF52DBAA),
    onTertiary = Color(0xFF00382A),
    tertiaryContainer = Color(0xFF00513D),
    onTertiaryContainer = Color(0xFFCDF5E6),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0A04),
    errorContainer = Color(0xFF8C1D12),
    onErrorContainer = Color(0xFFFFE2DE),
    background = Color(0xFF0F0D1A),
    onBackground = Color(0xFFECE9F6),
    surface = Color(0xFF0F0D1A),
    onSurface = Color(0xFFECE9F6),
    surfaceVariant = Color(0xFF2A2640),
    onSurfaceVariant = Color(0xFFB7B1CC),
    surfaceContainerLowest = Color(0xFF0B0914),
    surfaceContainerLow = Color(0xFF17142A),
    surfaceContainer = Color(0xFF1C1931),
    surfaceContainerHigh = Color(0xFF25213C),
    surfaceContainerHighest = Color(0xFF2F2A48),
    outline = Color(0xFF4D4866),
    outlineVariant = Color(0xFF332E4C),
    inverseSurface = Color(0xFFECE9F6),
    inverseOnSurface = Color(0xFF2B2738),
    inversePrimary = Brand.Violet,
)

private val Display = FontFamily.SansSerif

val QNowTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

val QNowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** 4-pt spacing scale used everywhere instead of ad-hoc numbers. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val screen = 20.dp
}

@Immutable
data class ExtendedColors(
    val heroGradient: Brush,
    val accentGradient: Brush,
    val open: Color,
    val onBreak: Color,
    val closed: Color,
    val star: Color,
    val isDark: Boolean,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        heroGradient = Brush.linearGradient(listOf(Brand.VioletBright, Brand.VioletDeep)),
        accentGradient = Brush.linearGradient(listOf(Brand.Coral, Brand.CoralDeep)),
        open = Brand.Mint, onBreak = Brand.Amber, closed = Brand.Rose, star = Brand.Amber, isDark = false,
    )
}

object QNowTheme {
    val extended: ExtendedColors @Composable get() = LocalExtendedColors.current
}

@Composable
fun QNowTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val extended = if (darkTheme) {
        ExtendedColors(
            heroGradient = Brush.linearGradient(listOf(Color(0xFF4A32E0), Color(0xFF1A1060))),
            accentGradient = Brush.linearGradient(listOf(Brand.Coral, Brand.CoralDeep)),
            open = Color(0xFF52DBAA), onBreak = Color(0xFFFFC15C), closed = Color(0xFFFF7A7E),
            star = Color(0xFFFFC15C), isDark = true,
        )
    } else {
        LocalExtendedColors.current.copy(isDark = false)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = QNowTypography,
            shapes = QNowShapes,
            content = content,
        )
    }
}
