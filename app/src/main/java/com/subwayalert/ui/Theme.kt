package com.subwayalert.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue500 = Color(0xFF1976D2)
private val Blue700 = Color(0xFF1565C0)
private val Orange500 = Color(0xFFFF9800)

private val LightColorPalette = lightColors(primary = Blue500, secondary = Orange500)
private val DarkColorPalette = darkColors(primary = Blue500, secondary = Orange500)

@Composable
fun SubwayAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = LightColorPalette, content = content)
}
