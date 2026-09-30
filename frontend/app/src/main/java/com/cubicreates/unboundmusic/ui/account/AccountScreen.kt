/*
 * Package: com.cubicreates.unboundmusic.ui.account
 * File: AccountScreen.kt
 * Purpose: Production coordinator AccountScreen routing to SignedInAccountScreen (authenticated)
 *          or GuestAccountScreen (offline/guest mode).
 * Subsystem: Account / Dual-Mode Coordinator
 */

package com.cubicreates.unboundmusic.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    isYouTubeConnected: Boolean = false,
    accountName: String? = null,
    userAvatarUrl: String? = null,
    savedGB: Double = 0.0,
    localTracksCount: Int = 0,
    cloudTracksCount: Int = 0,
    onConnectClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    // Dual-Mode Routing:
    // Mode 1: Logged-in YouTube user -> SignedInAccountScreen
    // Mode 2: Guest / Offline user -> GuestAccountScreen
    if (isYouTubeConnected) {
        SignedInAccountScreen(
            modifier = modifier,
            accountName = accountName,
            userAvatarUrl = userAvatarUrl,
            savedGB = savedGB,
            localTracksCount = localTracksCount,
            cloudTracksCount = cloudTracksCount,
            onSyncClick = onSyncClick,
            onDisconnectClick = onDisconnectClick,
            onClose = onClose
        )
    } else {
        GuestAccountScreen(
            modifier = modifier,
            savedGB = savedGB,
            localTracksCount = localTracksCount,
            onConnectClick = onConnectClick,
            onClose = onClose
        )
    }
}
