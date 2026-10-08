/*
 * Package: com.cubicreates.unboundmusic.ui.theme
 * File: ThemeTokens.kt
 * Purpose: Centralized design system tokens for spacing, corner radii, icon sizes, and elevations.
 * Subsystem: Design System & Styling
 */

package com.cubicreates.unboundmusic.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Spacing {
    val None: Dp = 0.dp
    val TwoXs: Dp = 2.dp
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Base: Dp = 16.dp
    val Large: Dp = 20.dp
    val ExtraLarge: Dp = 24.dp
    val TwoExtraLarge: Dp = 32.dp
    val ThreeExtraLarge: Dp = 48.dp
    val BottomBarPadding: Dp = 80.dp
}

object CornerRadius {
    val None: Dp = 0.dp
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val ExtraLarge: Dp = 20.dp
    val TwoExtraLarge: Dp = 24.dp
    val Full: Dp = 999.dp

    val ShapeSmall = RoundedCornerShape(Small)
    val ShapeMedium = RoundedCornerShape(Medium)
    val ShapeLarge = RoundedCornerShape(Large)
    val ShapeExtraLarge = RoundedCornerShape(ExtraLarge)
    val ShapeTwoExtraLarge = RoundedCornerShape(TwoExtraLarge)
    val ShapePill = RoundedCornerShape(Full)
}

object IconSize {
    val ExtraSmall: Dp = 14.dp
    val Small: Dp = 18.dp
    val Medium: Dp = 22.dp
    val Base: Dp = 24.dp
    val Large: Dp = 32.dp
    val ExtraLarge: Dp = 44.dp
    val Hero: Dp = 64.dp
}

object Elevation {
    val None: Dp = 0.dp
    val Subtle: Dp = 2.dp
    val Low: Dp = 4.dp
    val Medium: Dp = 8.dp
    val High: Dp = 16.dp
    val Modal: Dp = 24.dp
}
