package com.trustlens.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A deliberately restrained, security-product palette. Risk colours are NOT defined here:
// they belong to the risk level itself and are resolved from RiskLevel so that a Critical
// verdict looks identical everywhere it is shown.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4D8F),
    onPrimary = Color.White,
    surface = Color(0xFFFBFCFE),
    background = Color(0xFFF4F6FA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC4FF),
    onPrimary = Color(0xFF00305F),
    surface = Color(0xFF12151A),
    background = Color(0xFF0C0E12),
)

@Composable
fun TrustLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
