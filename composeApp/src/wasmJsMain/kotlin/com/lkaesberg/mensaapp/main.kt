package com.lkaesberg.mensaapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.lkaesberg.mensaapp.ui.MensaAppTheme
import com.russhwolf.settings.Settings
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun main() {
    // Enable mouse scrolling/dragging for the entire document
    document.body?.style?.setProperty("touch-action", "pan-y")
    
    ComposeViewport(document.body!!) {
        val settings = remember { Settings() }
        var isDarkMode by remember { mutableStateOf(settings.getBoolean("dark_mode", false)) }
        
        // Background colors based on theme
        val backgroundColor = if (isDarkMode) {
            Color(0xFF1a1a2e) // Dark blue-gray for dark mode
        } else {
            Color(0xFFe8eef7) // Light blue-gray for light mode
        }
        
        val phoneFrameColor = if (isDarkMode) {
            Color(0xFF2d2d44) // Darker frame for dark mode
        } else {
            Color(0xFF3a3a4a) // Dark frame for light mode (phones are usually dark)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            // Check if we're on a mobile-like aspect ratio (width < 500dp or aspect ratio < 0.7)
            val isMobileView = maxWidth < 500.dp || (maxWidth / maxHeight) < 0.7f
            
            if (isMobileView) {
                // Full screen without phone frame on mobile
                MensaAppTheme(useDarkTheme = isDarkMode) {
                    CanteenMealsScreen(
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = {
                            isDarkMode = !isDarkMode
                            settings.putBoolean("dark_mode", isDarkMode)
                        }
                    )
                }
            } else {
                // Phone frame on desktop
                Box(
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(40.dp),
                            ambientColor = Color.Black.copy(alpha = 0.3f),
                            spotColor = Color.Black.copy(alpha = 0.3f)
                        )
                        .clip(RoundedCornerShape(40.dp))
                        .background(phoneFrameColor)
                        .border(
                            width = 3.dp,
                            color = if (isDarkMode) Color(0xFF404060) else Color(0xFF4a4a5a),
                            shape = RoundedCornerShape(40.dp)
                        )
                        .padding(8.dp)
                ) {
                    // Phone screen
                    Surface(
                        modifier = Modifier
                            .width(420.dp)
                            .height(844.dp)
                            .clip(RoundedCornerShape(32.dp)),
                        color = Color.Transparent
                    ) {
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
                }
            }
        }
    }
}