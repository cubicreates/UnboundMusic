package com.cubicreates.unboundmusic.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = UnboundPrimary,
    onPrimary = OnPrimary,
    primaryContainer = UnboundPrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = UnboundSecondary,
    onSecondary = OnSecondary,
    secondaryContainer = UnboundSecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = UnboundTertiary,
    onTertiary = OnTertiary,
    background = UnboundBackground,
    onBackground = OnSurface,
    surface = UnboundSurface,
    onSurface = OnSurface,
    surfaceVariant = UnboundSurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceVariant,
    outline = UnboundOutline,
    outlineVariant = UnboundOutlineVariant
)

@Composable
fun UnboundMusicTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = UnboundBackground.toArgb()
            window.navigationBarColor = UnboundBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
