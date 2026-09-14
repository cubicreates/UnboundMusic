/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: NowPlayingStyle.kt
 * Purpose: Defines the selectable visual presentation styles for the Now Playing full-screen player.
 * Subsystem: Player UI
 */

package com.cubicreates.unboundmusic.ui.player

enum class NowPlayingStyle(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    SPOTIFY(
        title = "Spotify Canvas",
        subtitle = "Full-bleed artwork, tactile slider, 5-button control deck & embedded lyrics card",
        badge = "Standard"
    ),
    APPLE_MUSIC(
        title = "Apple Music Glass",
        subtitle = "Frosted glass aesthetic, prominent karaoke synced lyrics & clean typography",
        badge = "Frosted"
    ),
    M3_EXPRESSIVE(
        title = "M3 Expressive",
        subtitle = "Google Material 3 Expressive layout with animated sine-wave seek bar & pill controls",
        badge = "Dynamic"
    )
}
