package com.lkaesberg.mensaapp

import androidx.compose.runtime.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.lkaesberg.mensaapp.ui.MensaAppTheme
import com.russhwolf.settings.Settings

@Composable
@Preview
fun App() {
    val settings = remember { Settings() }
    var isDarkMode by remember { mutableStateOf(settings.getBoolean("dark_mode", false)) }
    
    MensaAppTheme(useDarkTheme = isDarkMode) {
        CanteenMealsScreen(
            isDarkMode = isDarkMode,
            onToggleDarkMode = { 
                isDarkMode = !isDarkMode
                settings.putBoolean("dark_mode", isDarkMode)
            }
        )
    }
}