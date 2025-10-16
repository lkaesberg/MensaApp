package com.lkaesberg.mensaapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006876),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA4EDFF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A6267),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8EC),
    onSecondaryContainer = Color(0xFF051F23),
    tertiary = Color(0xFF525E7D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDAE2FF),
    onTertiaryContainer = Color(0xFF0E1B37),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAFB),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF5FAFB),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDBE4E7),
    onSurfaceVariant = Color(0xFF3F484B),
    outline = Color(0xFF6F797C),
    outlineVariant = Color(0xFFBFC8CB),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D3132),
    inverseOnSurface = Color(0xFFEFF1F2),
    inversePrimary = Color(0xFF53D7F0),
    surfaceTint = Color(0xFF006876),
    surfaceContainerHighest = Color(0xFFDEE3E5),
    surfaceContainer = Color(0xFFE9EDEF),
    surfaceContainerLow = Color(0xFFEFF4F5),
    surfaceContainerLowest = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD0E8),
    onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF004F59),
    onPrimaryContainer = Color(0xFFA4EDFF),
    secondary = Color(0xFFB0CCD1),
    onSecondary = Color(0xFF1B3438),
    secondaryContainer = Color(0xFF324B4F),
    onSecondaryContainer = Color(0xFFCCE8EC),
    tertiary = Color(0xFFB8C6EA),
    onTertiary = Color(0xFF23304E),
    tertiaryContainer = Color(0xFF3A4765),
    onTertiaryContainer = Color(0xFFDAE2FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1416),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0F1416),
    onSurface = Color(0xFFDEE3E5),
    surfaceVariant = Color(0xFF3F484B),
    onSurfaceVariant = Color(0xFFBFC8CB),
    outline = Color(0xFF899295),
    outlineVariant = Color(0xFF3F484B),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDEE3E5),
    inverseOnSurface = Color(0xFF2D3132),
    inversePrimary = Color(0xFF006876),
    surfaceTint = Color(0xFF4DD0E8),
    surfaceContainerHighest = Color(0xFF32373A),
    surfaceContainer = Color(0xFF1D2022),
    surfaceContainerLow = Color(0xFF181C1E),
    surfaceContainerLowest = Color(0xFF0A0F10)
)

@Composable
fun MensaAppTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
} 