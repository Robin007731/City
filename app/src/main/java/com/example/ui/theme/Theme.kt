package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AldeaMetropolisTheme(
    eraAccent: Color = EraAldea,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = eraAccent,
            onPrimary = Color(0xFF0C140E),
            primaryContainer = eraAccent.copy(alpha = 0.22f),
            onPrimaryContainer = InkDark,
            secondary = InkDark,
            onSecondary = BgDark,
            secondaryContainer = PanelElevatedDark,
            onSecondaryContainer = InkDark,
            background = BgDark,
            onBackground = InkDark,
            surface = PanelDark,
            onSurface = InkDark,
            surfaceVariant = PanelElevatedDark,
            onSurfaceVariant = MutedDark,
            outline = OutlineDark,
            error = DangerRed,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = eraAccent,
            onPrimary = Color(0xFF0C140E),
            primaryContainer = eraAccent.copy(alpha = 0.20f),
            onPrimaryContainer = InkLight,
            secondary = InkLight,
            onSecondary = PanelLight,
            secondaryContainer = BgLight,
            onSecondaryContainer = InkLight,
            background = BgLight,
            onBackground = InkLight,
            surface = PanelLight,
            onSurface = InkLight,
            surfaceVariant = BgLight,
            onSurfaceVariant = MutedLight,
            outline = OutlineLight,
            error = DangerRed,
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
