package com.example.mensaapp

import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.example.mensaapp.ui.MensaAppTheme

@Composable
@Preview
fun App() {
    MensaAppTheme {
        CanteenMealsScreen()
    }
}