/*
 * Package: com.cubicreates.unboundmusic.ui.shazam
 * File: ShazamScreen.kt
 * Purpose: Dual-mode coordinator routing to SignedInShazamScreen (for logged-in YouTube users)
 *          or GuestShazamScreen (for offline/guest users) with acoustic radar, interactive waveform, and recognition history.
 * Subsystem: Domain UI / Acoustic Recognition Coordinator
 */

package com.cubicreates.unboundmusic.ui.shazam

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cubicreates.unboundmusic.ui.components.RecognizedTrackVariant
import com.cubicreates.unboundmusic.ui.components.ShazamMode
import com.cubicreates.unboundmusic.ui.components.TrackItem

@Composable
fun ShazamScreen(
    modifier: Modifier = Modifier,
    isYouTubeConnected: Boolean = false,
    isListening: Boolean = false,
    audioAmplitude: Float = 0f,
    statusMessage: String? = null,
    accountName: String? = null,
    lastRecognizedTrack: TrackItem? = null,
    recognizedVariants: List<RecognizedTrackVariant> = emptyList(),
    shazamHistory: List<TrackItem> = emptyList(),
    isFavorite: Boolean = false,
    shazamMode: ShazamMode = ShazamMode.ACOUSTIC,
    onModeChange: (ShazamMode) -> Unit = {},
    onLaunchGoogleSoundSearch: () -> Unit = {},
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
            audioAmplitude = audioAmplitude,
            statusMessage = statusMessage,
            accountName = accountName,
            lastRecognizedTrack = lastRecognizedTrack,
            recognizedVariants = recognizedVariants,
            shazamHistory = shazamHistory,
            isFavorite = isFavorite,
            shazamMode = shazamMode,
            onModeChange = onModeChange,
            onLaunchGoogleSoundSearch = onLaunchGoogleSoundSearch,
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
            audioAmplitude = audioAmplitude,
            statusMessage = statusMessage,
            lastRecognizedTrack = lastRecognizedTrack,
            recognizedVariants = recognizedVariants,
            shazamHistory = shazamHistory,
            shazamMode = shazamMode,
            onModeChange = onModeChange,
            onLaunchGoogleSoundSearch = onLaunchGoogleSoundSearch,
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
