package com.pacuka.magyarkozlony.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val GovBlue = Color(0xFF003366)
private val GovGold = Color(0xFFC5A059)
private val NavyBackground = Color(0xFF001F3F)
private val NavySurface = Color(0xFF002D54)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color.Black,
    secondary = GovGold,
    background = Color.Black,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    outline = Color(0xFF8AB4F8).copy(alpha = 0.3f)
)

private val LightColorScheme = lightColorScheme(
    primary = GovBlue,
    onPrimary = Color.White,
    secondary = GovGold,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    outline = GovBlue.copy(alpha = 0.2f)
)

private val NavyColorScheme = darkColorScheme(
    primary = GovGold,
    onPrimary = Color.Black,
    secondary = Color(0xFF0074D9),
    background = NavyBackground,
    surface = NavySurface,
    onSurface = Color.White,
    outline = GovGold.copy(alpha = 0.4f)
)

@Composable
fun MagyarKozlonyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isNavy: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isNavy -> NavyColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
