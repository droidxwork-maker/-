package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NavaDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = IndigoGlow,
    onSecondary = IndigoOnSecondary,
    secondaryContainer = IndigoContainer,
    onSecondaryContainer = OnIndigoContainer,
    tertiary = AmberSpark,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = SlateElevated,
    outline = OutlineDark,
    outlineVariant = OutlineSubtleDark,
    error = ErrorRedDark,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val NavaLightColorScheme = lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = CyanLightContainer,
    onPrimaryContainer = OnCyanLightContainer,
    secondary = IndigoDeep,
    onSecondary = Color.White,
    secondaryContainer = IndigoLightContainer,
    onSecondaryContainer = OnIndigoLightContainer,
    tertiary = AmberDeep,
    onTertiary = Color.White,
    tertiaryContainer = AmberLightContainer,
    onTertiaryContainer = OnAmberLightContainer,
    background = FrostBg,
    onBackground = TextPrimaryLight,
    surface = WhiteSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = MistSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerHigh = Color(0xFFF1F5F9),
    outline = OutlineLight,
    outlineVariant = Color(0xFFE2E8F0),
    error = ErrorRedLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val NavaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NavaAiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NavaDarkColorScheme else NavaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NavaTypography,
        shapes = NavaShapes,
        content = content
    )
}
