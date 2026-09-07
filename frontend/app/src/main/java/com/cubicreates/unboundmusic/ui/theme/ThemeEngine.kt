/*
 * Package: com.cubicreates.unboundmusic.ui.theme
 * File: ThemeEngine.kt
 * Purpose: Adaptive Studio Theme Engine supporting Studio Dark (Obsidian/Cyan),
 *          Neon Violet (Cyberpunk Magenta), Matrix Terminal (Phosphor Emerald),
 *          and Material You (Android 12+ Dynamic Monet).
 * Subsystem: Design System / UI Theming
 * Concurrency: Main thread UI state rendering.
 */

package com.cubicreates.unboundmusic.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 4 Supported Studio Theme Presets.
 */
enum class AppThemePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val previewPrimary: Color,
    val previewBackground: Color
) {
    STUDIO_DARK(
        id = "studio_dark",
        displayName = "Studio Obsidian",
        description = "Precision audio cyan and deep obsidian glass",
        previewPrimary = Color(0xFF4CD6FB),
        previewBackground = Color(0xFF131313)
    ),
    NEON_VIOLET(
        id = "neon_violet",
        displayName = "Neon Violet",
        description = "Cyberpunk ultraviolet, neon magenta and deep purple",
        previewPrimary = Color(0xFFD67BFF),
        previewBackground = Color(0xFF0D0814)
    ),
    MATRIX_TERMINAL(
        id = "matrix_terminal",
        displayName = "Matrix Terminal",
        description = "Phosphor emerald green and OLED absolute black",
        previewPrimary = Color(0xFF00FF66),
        previewBackground = Color(0xFF040805)
    ),
    MATERIAL_YOU(
        id = "material_you",
        displayName = "Material You",
        description = "System wallpaper adaptive palette (Android 12+)",
        previewPrimary = Color(0xFFB1C8D8),
        previewBackground = Color(0xFF181C20)
    );

    companion object {
        fun fromId(id: String): AppThemePreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STUDIO_DARK
        }
    }
}

// 1. Default Studio Obsidian Color Scheme
val StudioDarkColorScheme = darkColorScheme(
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

// 2. Neon Violet Cyberpunk Color Scheme
val NeonVioletColorScheme = darkColorScheme(
    primary = Color(0xFFD67BFF),
    onPrimary = Color(0xFF4A0072),
    primaryContainer = Color(0xFFBD00FF),
    onPrimaryContainer = Color(0xFFF8E5FF),
    secondary = Color(0xFF9E6BFF),
    onSecondary = Color(0xFF28006E),
    secondaryContainer = Color(0xFF5D1294),
    onSecondaryContainer = Color(0xFFF0DDFF),
    tertiary = Color(0xFFFF5CBE),
    onTertiary = Color(0xFF520038),
    background = Color(0xFF0D0814),
    onBackground = Color(0xFFF3EAF8),
    surface = Color(0xFF130C1C),
    onSurface = Color(0xFFF3EAF8),
    surfaceVariant = Color(0xFF221630),
    onSurfaceVariant = Color(0xFFCFBED8),
    outline = Color(0xFF9C87A6),
    outlineVariant = Color(0xFF4E3D58)
)

// 3. Matrix Terminal OLED Green Color Scheme
val MatrixTerminalColorScheme = darkColorScheme(
    primary = Color(0xFF00FF66),
    onPrimary = Color(0xFF003B15),
    primaryContainer = Color(0xFF008F39),
    onPrimaryContainer = Color(0xFFE0FFEA),
    secondary = Color(0xFF33FF88),
    onSecondary = Color(0xFF003314),
    secondaryContainer = Color(0xFF006B2B),
    onSecondaryContainer = Color(0xFFC0FFD3),
    tertiary = Color(0xFF00E5A3),
    onTertiary = Color(0xFF003825),
    background = Color(0xFF040805),
    onBackground = Color(0xFFE2F3E7),
    surface = Color(0xFF080F0A),
    onSurface = Color(0xFFE2F3E7),
    surfaceVariant = Color(0xFF111D14),
    onSurfaceVariant = Color(0xFFA5C5AE),
    outline = Color(0xFF72937C),
    outlineVariant = Color(0xFF2A3D30)
)

/**
 * Resolves the appropriate ColorScheme for a given theme preset and context.
 */
fun resolveColorScheme(preset: AppThemePreset, context: Context): ColorScheme {
    return when (preset) {
        AppThemePreset.STUDIO_DARK -> StudioDarkColorScheme
        AppThemePreset.NEON_VIOLET -> NeonVioletColorScheme
        AppThemePreset.MATRIX_TERMINAL -> MatrixTerminalColorScheme
        AppThemePreset.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    dynamicDarkColorScheme(context)
                } catch (_: Exception) {
                    StudioDarkColorScheme
                }
            } else {
                StudioDarkColorScheme
            }
        }
    }
}
