package com.lkaesberg.mensaapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import com.lkaesberg.mensaapp.ui.MensaAppTheme
import com.russhwolf.settings.Settings
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun main() {
    document.body?.style?.setProperty("touch-action", "pan-y")

    ComposeViewport(document.body!!) {
        val settings = remember { Settings() }
        var isDarkMode by remember { mutableStateOf(settings.getBoolean("dark_mode", false)) }

        MensaAppTheme(useDarkTheme = isDarkMode) {
            CanteenMealsScreen(
                modifier = Modifier.fillMaxSize(),
                isDarkMode = isDarkMode,
                onToggleDarkMode = {
                    isDarkMode = !isDarkMode
                    settings.putBoolean("dark_mode", isDarkMode)
                }
            )
        }
    }
}
