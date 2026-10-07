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
    hummingHistory: List<TrackItem> = emptyList(),
    lastHummedTrack: TrackItem? = null,
    hummedVariants: List<RecognizedTrackVariant> = emptyList(),
    isFavorite: Boolean = false,
    shazamMode: ShazamMode = ShazamMode.ACOUSTIC,
    recordingDurationSeconds: Int = 0,
    onModeChange: (ShazamMode) -> Unit = {},
    onLaunchGoogleSoundSearch: () -> Unit = {},
    onStartListening: () -> Unit = {},
    onStopListening: () -> Unit = {},
    onDismissRecognized: () -> Unit = {},
    onDismissHummed: () -> Unit = {},
    onPlayTrack: (TrackItem) -> Unit = {},
    onStartRadio: (TrackItem) -> Unit = {},
    onToggleFavorite: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onDownload: (TrackItem) -> Unit = {},
    onConnectYouTubeClick: () -> Unit = {},
    onVoiceSearchClick: () -> Unit = {}
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
            hummingHistory = hummingHistory,
            lastHummedTrack = lastHummedTrack,
            hummedVariants = hummedVariants,
            isFavorite = isFavorite,
            shazamMode = shazamMode,
            recordingDurationSeconds = recordingDurationSeconds,
            onModeChange = onModeChange,
            onLaunchGoogleSoundSearch = onLaunchGoogleSoundSearch,
            onStartListening = onStartListening,
            onStopListening = onStopListening,
            onDismissRecognized = onDismissRecognized,
            onDismissHummed = onDismissHummed,
            onPlayTrack = onPlayTrack,
            onStartRadio = onStartRadio,
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = onAddToPlaylist,
            onDownload = onDownload,
            onVoiceSearchClick = onVoiceSearchClick
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
            hummingHistory = hummingHistory,
            lastHummedTrack = lastHummedTrack,
            hummedVariants = hummedVariants,
            shazamMode = shazamMode,
            recordingDurationSeconds = recordingDurationSeconds,
            onModeChange = onModeChange,
            onLaunchGoogleSoundSearch = onLaunchGoogleSoundSearch,
            onStartListening = onStartListening,
            onStopListening = onStopListening,
            onDismissRecognized = onDismissRecognized,
            onDismissHummed = onDismissHummed,
            onPlayTrack = onPlayTrack,
            onStartRadio = onStartRadio,
            onAddToPlaylist = onAddToPlaylist,
            onDownload = onDownload,
            onConnectYouTubeClick = onConnectYouTubeClick,
            onVoiceSearchClick = onVoiceSearchClick
        )
    }
}
