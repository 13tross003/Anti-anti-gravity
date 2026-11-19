package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AntigravityDarkColorScheme = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = Color(0xFF0A192F),
    primaryContainer = Color(0xFF1F324E),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = GoogleGreenDark,
    onSecondary = Color(0xFF0B2915),
    secondaryContainer = Color(0xFF173621),
    onSecondaryContainer = Color(0xFFC4EED0),
    tertiary = GoogleYellowDark,
    onTertiary = Color(0xFF2B2000),
    tertiaryContainer = Color(0xFF3A2E0E),
    onTertiaryContainer = Color(0xFFFEEFC3),
    error = GoogleRedDark,
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF441816),
    onErrorContainer = Color(0xFFFAD2CF),
    background = AntigravityObsidian,
    onBackground = Color(0xFFE8EAED),
    surface = AntigravityObsidian,
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = AntigravitySurfaceElevated,
    onSurfaceVariant = Color(0xFF9AA0A6),
    surfaceContainerLowest = Color(0xFF0D0E12),
    surfaceContainerLow = AntigravitySurfaceElevated,
    surfaceContainer = AntigravitySurfaceContainer,
    surfaceContainerHigh = AntigravitySurfaceHighest,
    surfaceContainerHighest = Color(0xFF323746),
    outline = AntigravityBorderDark,
    outlineVariant = Color(0xFF232732),
)

private val AntigravityLightColorScheme = lightColorScheme(
    primary = AntigravityObsidian,
    onPrimary = AntigravityWhite,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF174EA6),
    secondary = GoogleGreen,
    onSecondary = AntigravityWhite,
    secondaryContainer = Color(0xFFE6F4EA),
    onSecondaryContainer = Color(0xFF0D652D),
    tertiary = GoogleBlue,
    onTertiary = AntigravityWhite,
    tertiaryContainer = Color(0xFFFEF7E0),
    onTertiaryContainer = Color(0xFFB06000),
    error = GoogleRed,
    onError = AntigravityWhite,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFFA50E0E),
    background = AntigravityWhite,
    onBackground = AntigravityObsidian,
    surface = AntigravityWhite,
    onSurface = AntigravityObsidian,
    surfaceVariant = AntigravityLightContainer,
    onSurfaceVariant = AntigravityTextCharcoal,
    surfaceContainerLowest = AntigravityWhite,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = AntigravityLightContainer,
    surfaceContainerHigh = AntigravityLightContainerHigh,
    surfaceContainerHighest = Color(0xFFDFE3EC),
    outline = AntigravityBorderLight,
    outlineVariant = Color(0xFFEAEDF2),
)

val AntigravityShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AntigravityTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AntigravityDarkColorScheme else AntigravityLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalAntigravityColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AntigravityShapes,
            content = content,
        )
    }
}
