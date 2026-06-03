package com.lkaesberg.mensaapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun main() {
    document.body?.style?.setProperty("touch-action", "pan-y")
    // Render into #app-container so the optional download banner can sit above the canvas.
    val container = document.getElementById("app-container") as HTMLElement
    ComposeViewport(container) {
        App()
    }
}
