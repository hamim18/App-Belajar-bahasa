package com.belajarbahasa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warna sesuai section 6.1 Design System di Konsep-program-learn.md
private val PrimaryColor = Color(0xFF2C3E7A)
private val SecondaryColor = Color(0xFFE8A87C)
private val BackgroundColor = Color(0xFFF5F6FA)
private val SurfaceColor = Color(0xFFFFFFFF)
private val ErrorColor = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    background = BackgroundColor,
    surface = SurfaceColor,
    error = ErrorColor
)

@Composable
fun BelajarBahasaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
