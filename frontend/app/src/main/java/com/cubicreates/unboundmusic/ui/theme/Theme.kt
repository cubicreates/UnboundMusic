package com.cubicreates.unboundmusic.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
    themePreset: AppThemePreset = AppThemePreset.STUDIO_DARK,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = resolveColorScheme(themePreset, context)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val activity = view.context.findActivity()
                if (activity != null) {
                    val window = activity.window
                    window.statusBarColor = colorScheme.background.toArgb()
                    window.navigationBarColor = colorScheme.background.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
                }
            } catch (e: Throwable) {
                Log.w("UnboundMusicTheme", "Failed to configure window insets/colors: ${e.message}")
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
