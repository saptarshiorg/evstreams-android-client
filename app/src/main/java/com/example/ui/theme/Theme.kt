package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = EvRedPrimary,
    onPrimary = Color.White,
    primaryContainer = EvRedDark,
    onPrimaryContainer = Color.White,
    secondary = EvGoldAccent,
    onSecondary = Color.Black,
    background = EvDarkBackground,
    onBackground = TextPrimaryDark,
    surface = EvDarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = EvDarkCard,
    onSurfaceVariant = TextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = EvRedPrimary,
    onPrimary = Color.White,
    primaryContainer = EvRedLight,
    onPrimaryContainer = Color.White,
    secondary = EvGoldAccent,
    onSecondary = Color.Black,
    background = EvLightBackground,
    onBackground = TextPrimaryLight,
    surface = EvLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = EvLightCard,
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun EVSportsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
