/*
 * Package: com.cubicreates.unboundmusic.ui.shazam
 * File: ShazamScreen.kt
 * Purpose: Dual-mode coordinator routing to SignedInShazamScreen (for logged-in YouTube users)
 *          or GuestShazamScreen (for offline/guest users) with acoustic radar and recognition history.
 * Subsystem: Domain UI / Acoustic Recognition Coordinator
 */

package com.cubicreates.unboundmusic.ui.shazam

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cubicreates.unboundmusic.ui.components.TrackItem

@Composable
fun ShazamScreen(
    modifier: Modifier = Modifier,
    isYouTubeConnected: Boolean = false,
    isListening: Boolean = false,
    statusMessage: String? = null,
    accountName: String? = null,
    lastRecognizedTrack: TrackItem? = null,
    shazamHistory: List<TrackItem> = emptyList(),
    isFavorite: Boolean = false,
    onStartListening: () -> Unit = {},
    onDismissRecognized: () -> Unit = {},
    onPlayTrack: (TrackItem) -> Unit = {},
    onStartRadio: (TrackItem) -> Unit = {},
    onToggleFavorite: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onDownload: (TrackItem) -> Unit = {},
    onConnectYouTubeClick: () -> Unit = {}
) {
    if (isYouTubeConnected) {
        SignedInShazamScreen(
            modifier = modifier,
            isListening = isListening,
            statusMessage = statusMessage,
            accountName = accountName,
            lastRecognizedTrack = lastRecognizedTrack,
            shazamHistory = shazamHistory,
            isFavorite = isFavorite,
            onStartListening = onStartListening,
            onDismissRecognized = onDismissRecognized,
            onPlayTrack = onPlayTrack,
            onStartRadio = onStartRadio,
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = onAddToPlaylist,
            onDownload = onDownload
        )
    } else {
        GuestShazamScreen(
            modifier = modifier,
            isListening = isListening,
            statusMessage = statusMessage,
            lastRecognizedTrack = lastRecognizedTrack,
            shazamHistory = shazamHistory,
            onStartListening = onStartListening,
            onDismissRecognized = onDismissRecognized,
            onPlayTrack = onPlayTrack,
            onStartRadio = onStartRadio,
            onAddToPlaylist = onAddToPlaylist,
            onDownload = onDownload,
            onConnectYouTubeClick = onConnectYouTubeClick
        )
    }
}
