package com.example.mensaapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006684),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDE9FF),
    onPrimaryContainer = Color(0xFF001E2B),
    secondary = Color(0xFF4D616C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E6F2),
    onSecondaryContainer = Color(0xFF081E27),
    tertiary = Color(0xFF00696F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF97F0F7),
    onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFB3261E),
    onError = Color.White,
    background = Color(0xFFF8FCFF),
    onBackground = Color(0xFF001F25),
    surface = Color(0xFFF8FCFF),
    onSurface = Color(0xFF001F25),
    outline = Color(0xFF71787E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64D3FF),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF004D66),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFFB4CAD6),
    onSecondary = Color(0xFF1F333D),
    secondaryContainer = Color(0xFF364954),
    onSecondaryContainer = Color(0xFFD0E6F2),
    tertiary = Color(0xFF4FD8DE),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF004E55),
    onTertiaryContainer = Color(0xFF97F0F7),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    background = Color(0xFF001F25),
    onBackground = Color(0xFFA6EEFF),
    surface = Color(0xFF001F25),
    onSurface = Color(0xFFA6EEFF),
    outline = Color(0xFF8A9298)
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