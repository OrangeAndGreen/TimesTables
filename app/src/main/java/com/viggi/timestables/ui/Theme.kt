package com.viggi.timestables.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RightGreen = Color(0xFF2E7D32)
val WrongRed = Color(0xFFC62828)
val TimeoutAmber = Color(0xFFEF6C00)

private val Light = lightColorScheme(
    primary = Color(0xFF00796B),
    secondary = Color(0xFF5E35B1),
    tertiary = Color(0xFFF9A825),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    secondary = Color(0xFFB39DDB),
    tertiary = Color(0xFFFFD54F),
)

@Composable
fun TimesTablesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
