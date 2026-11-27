/*
 * Package: com.cubicreates.unboundmusic.ui.settings
 * File: SettingsScreen.kt
 * Purpose: Unified Account, Studio Preferences & System Settings screen (Profile tab).
 *          Supports real Guest/Offline and Signed-In YouTube states cleanly without dummy data,
 *          pairing modern grouped containers, appearance presets, and real Go backend actions.
 * Subsystem: Settings / Profile UI
 */

package com.cubicreates.unboundmusic.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.AudioQuality
import com.cubicreates.unboundmusic.ui.home.components.MediaFeedScaffold
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLow
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLowest

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isYouTubeConnected: Boolean = false,
    accountName: String = "Local User",
    userAvatarUrl: String? = null,
    cachePurgeStatus: String? = null,
    downloadedTracksCount: Int = 0,
    downloadsSizeMB: Double = 0.0,
    cacheSizeMB: Double = 0.0,
    localTracksCount: Int = 0,
    onClose: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onAutoEqClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    onYouTubeSyncClick: () -> Unit = {},
    onYouTubeResyncClick: () -> Unit = {},
    onDisconnectYouTubeClick: () -> Unit = {},
    onCheckUpdateClick: () -> Unit = {},
    onPurgeCacheClick: () -> Unit = {},
    onCleanStorageForUninstallClick: () -> Unit = {},
    autoDownloadLikedSongs: Boolean = false,
    skipSilenceEnabled: Boolean = false,
    normalizeVolumeEnabled: Boolean = false,
    sponsorBlockEnabled: Boolean = true,
    lockscreenWallpaperEnabled: Boolean = true,
    vlcAutoScanEnabled: Boolean = true,
    streamingQuality: AudioQuality = AudioQuality.HIGH,
    downloadQuality: AudioQuality = AudioQuality.HIGH,
    onAutoDownloadLikedSongsChange: (Boolean) -> Unit = {},
    onSkipSilenceChange: (Boolean) -> Unit = {},
    onNormalizeVolumeChange: (Boolean) -> Unit = {},
    onSponsorBlockChange: (Boolean) -> Unit = {},
    onLockscreenWallpaperChange: (Boolean) -> Unit = {},
    onVlcAutoScanChange: (Boolean) -> Unit = {},
    onStreamingQualityChange: (AudioQuality) -> Unit = {},
    onDownloadQualityChange: (AudioQuality) -> Unit = {},
    onOpenDownloadsHub: () -> Unit = {},
    onExportBackupClick: () -> Unit = {},
    onRestoreBackupClick: () -> Unit = {},
    enableBackHandler: Boolean = true
) {
    var showStreamingQualityDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showUninstallConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = enableBackHandler) {
        onClose()
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val powerManager = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(PowerManager::class.java)
        } else null
    }
    val isIgnoringBatteryOpt = remember(powerManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    Box(modifier = modifier.fillMaxSize()) {
        MediaFeedScaffold(
            modifier = Modifier.fillMaxSize(),
            listState = listState,
            bottomContentPadding = 148.dp
        ) {
            // ==================== 1. Header Block ====================
            item(key = "profile_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Account & Settings",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Manage account, audio quality & preferences",
                            fontSize = 13.sp,
                            color = OnSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(UnboundSurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Audio studio",
                            tint = UnboundPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // ==================== 2. Identity Hero Card (Guest & Signed-In) ====================
            item(key = "profile_identity_card") {
                IdentityHeroCard(
                    isYouTubeConnected = isYouTubeConnected,
                    accountName = accountName,
                    userAvatarUrl = userAvatarUrl,
                    streamingQuality = streamingQuality,
                    onConnectOrDisconnect = {
                        if (isYouTubeConnected) onDisconnectYouTubeClick() else onYouTubeSyncClick()
                    }
                )
            }

            // ==================== 3. Audio Quality & Sound ====================
            item(key = "profile_audio_section") {
                val downloadsSummary = if (downloadedTracksCount > 0 || downloadsSizeMB > 0.05) {
                    "$downloadedTracksCount downloaded tracks (${"%.1f".format(downloadsSizeMB)} MB)"
                } else {
                    "Manage cached tracks, offline songs & folders"
                }

                SettingsSectionBlock(title = "Audio Quality & Sound") {
                    SettingsGroupCard {
                        SettingsNavRow(
                            icon = Icons.Default.Tune,
                            title = "Sound Equalizer",
                            subtitle = "Fine-tune bass, mids, and treble",
                            onClick = onEqualizerClick
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Headphones,
                            title = "Headphone Tuning",
                            subtitle = "Optimized profiles for 4,000+ headphones",
                            onClick = onAutoEqClick
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Bedtime,
                            title = "Sleep Timer & Fade-Out",
                            subtitle = "Gradual volume decay on timer end",
                            onClick = onSleepTimerClick
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Streaming Audio Quality",
                            subtitle = "Wi-Fi & mobile bitrate",
                            trailingPill = streamingQuality.title.substringBefore(" "),
                            onClick = { showStreamingQualityDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Download,
                            title = "Download Audio Quality",
                            subtitle = "Offline storage encoding",
                            trailingPill = downloadQuality.title.substringBefore(" "),
                            onClick = { showDownloadQualityDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Folder,
                            title = "Downloads & Storage",
                            subtitle = downloadsSummary,
                            onClick = onOpenDownloadsHub
                        )
                    }
                }
            }

            // ==================== 4. Playback & Playlists ====================
            item(key = "profile_playback_section") {
                SettingsSectionBlock(title = "Playback & Playlists") {
                    SettingsGroupCard {
                        SettingsToggleRow(
                            title = "Auto-Download Liked Songs",
                            subtitle = "Automatically save favorites for offline listening",
                            checked = autoDownloadLikedSongs,
                            onCheckedChange = onAutoDownloadLikedSongsChange
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            title = "Skip Silence",
                            subtitle = "Eliminate dead air between tracks seamlessly",
                            checked = skipSilenceEnabled,
                            onCheckedChange = onSkipSilenceChange
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            title = "Loudness Normalization",
                            subtitle = "Consistent volume across diverse tracks",
                            checked = normalizeVolumeEnabled,
                            onCheckedChange = onNormalizeVolumeChange
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            title = "SponsorBlock Music Filter",
                            subtitle = "Skip non-music intros and chatter in songs",
                            checked = sponsorBlockEnabled,
                            onCheckedChange = onSponsorBlockChange
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            title = "Lock Screen Artwork Wallpaper",
                            subtitle = "Display live album artwork on lock screen",
                            checked = lockscreenWallpaperEnabled,
                            onCheckedChange = onLockscreenWallpaperChange
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            title = "Scan Device for New Music",
                            subtitle = "Automatically detect newly downloaded files",
                            checked = vlcAutoScanEnabled,
                            onCheckedChange = onVlcAutoScanChange
                        )
                    }
                }
            }

            // ==================== 6. Connected Accounts ====================
            item(key = "profile_accounts_section") {
                SettingsSectionBlock(
                    title = "Connected Accounts",
                    trailingCaption = if (isYouTubeConnected) "Synced" else "Local mode"
                ) {
                    SettingsGroupCard {
                        SettingsActionButtonRow(
                            icon = Icons.Default.Sync,
                            title = "YouTube Music Sync",
                            subtitle = if (isYouTubeConnected) "Synced & Online" else "Connect your library & playlists",
                            subtitleColor = if (isYouTubeConnected) UnboundSecondary else OnSurfaceVariant,
                            showSubtitleDot = isYouTubeConnected,
                            buttonText = if (isYouTubeConnected) "Sync Now" else "Connect",
                            isPrimaryButton = !isYouTubeConnected,
                            onButtonClick = {
                                if (isYouTubeConnected) onYouTubeResyncClick() else onYouTubeSyncClick()
                            }
                        )
                        SettingsRowDivider()
                        SettingsBadgeRow(
                            icon = Icons.Default.BatteryChargingFull,
                            title = "Battery Optimization",
                            subtitle = if (isIgnoringBatteryOpt) {
                                "Unrestricted background playback"
                            } else {
                                "Restricted • Tap to allow uninterrupted playback"
                            },
                            badgeText = if (isIgnoringBatteryOpt) "Optimized" else "Fix",
                            badgeColor = if (isIgnoringBatteryOpt) UnboundSecondary else Color(0xFFFFC594),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // ==================== 7. Storage & Backup ====================
            item(key = "profile_storage_section") {
                val cacheSubtitle = cachePurgeStatus
                    ?: "Free up temporary cached streams (${"%.1f".format(cacheSizeMB)} MB)"

                SettingsSectionBlock(title = "Storage & Backup") {
                    SettingsGroupCard {
                        SettingsNavRow(
                            icon = Icons.Default.FileUpload,
                            title = "Export Backup",
                            subtitle = "Save playlists, favorites & settings as JSON",
                            iconTint = OnSurfaceVariant,
                            onClick = onExportBackupClick
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Default.FileDownload,
                            title = "Restore Backup",
                            subtitle = "Import an existing unbound_backup.json",
                            iconTint = OnSurfaceVariant,
                            onClick = onRestoreBackupClick
                        )
                        SettingsRowDivider()
                        SettingsActionButtonRow(
                            icon = Icons.Default.CleaningServices,
                            title = "Clear Temp Cache",
                            subtitle = cacheSubtitle,
                            iconTint = OnSurfaceVariant,
                            buttonText = "Clear",
                            isPrimaryButton = false,
                            onButtonClick = onPurgeCacheClick
                        )
                        SettingsRowDivider()
                        SettingsDestructiveRow(
                            icon = Icons.Default.DeleteForever,
                            title = "Clean Engine & Uninstall",
                            subtitle = "Remove engine & AI weights, keep local music",
                            onClick = { showUninstallConfirmDialog = true }
                        )
                    }
                }
            }

            // ==================== 8. Centered Footer & Version ====================
            item(key = "profile_footer") {
                AppFooterSection(
                    localTracksCount = localTracksCount,
                    onCheckUpdateClick = onCheckUpdateClick
                )
            }
        }

        // ==================== Dialogs ====================
        if (showStreamingQualityDialog) {
            QualitySelectionDialog(
                title = "Streaming Audio Quality",
                selected = streamingQuality,
                onSelect = {
                    onStreamingQualityChange(it)
                    showStreamingQualityDialog = false
                },
                onDismiss = { showStreamingQualityDialog = false }
            )
        }

        if (showDownloadQualityDialog) {
            QualitySelectionDialog(
                title = "Download Audio Quality",
                selected = downloadQuality,
                onSelect = {
                    onDownloadQualityChange(it)
                    showDownloadQualityDialog = false
                },
                onDismiss = { showDownloadQualityDialog = false }
            )
        }

        if (showUninstallConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showUninstallConfirmDialog = false },
                title = {
                    Text(
                        text = "Clean Engine & Uninstall",
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                },
                text = {
                    Text(
                        text = "This stops the background engine and clears unpacked AI models and native binaries before opening Android's uninstaller.\n\nYour downloaded music files in device storage will be kept.",
                        color = OnSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showUninstallConfirmDialog = false
                            onCleanStorageForUninstallClick()
                        }
                    ) {
                        Text(
                            text = "Purge & Uninstall",
                            color = Color(0xFFFFB4AB),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUninstallConfirmDialog = false }) {
                        Text(text = "Cancel", color = OnSurface)
                    }
                },
                containerColor = UnboundSurfaceContainerHigh
            )
        }
    }
}

// ==================== Identity Card & Telemetry Pills ====================

@Composable
private fun IdentityHeroCard(
    isYouTubeConnected: Boolean,
    accountName: String,
    userAvatarUrl: String?,
    streamingQuality: AudioQuality,
    onConnectOrDisconnect: () -> Unit
) {
    val displayName = if (isYouTubeConnected && accountName.isNotBlank() && accountName != "Local User") {
        accountName
    } else {
        "Guest Listener"
    }
    val statusDotColor = if (isYouTubeConnected) UnboundSecondary else Color(0xFFFFC594)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = UnboundSurfaceContainer,
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle ambient cyan glow in upper-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 36.dp, y = (-36).dp)
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                UnboundPrimary.copy(alpha = if (isYouTubeConnected) 0.14f else 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(56.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(UnboundSurfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isYouTubeConnected && !userAvatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userAvatarUrl,
                                        contentDescription = "Profile photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (isYouTubeConnected && displayName.isNotBlank()) {
                                    Text(
                                        text = displayName.take(1).uppercase(),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = UnboundPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Guest listener",
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                                    .padding(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(UnboundSurfaceContainerLowest)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = displayName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isYouTubeConnected) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = UnboundPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                Text(
                                    text = if (isYouTubeConnected) "YouTube Synced • Active" else "Local & Offline Mode",
                                    fontSize = 11.5.sp,
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isYouTubeConnected) UnboundSurfaceContainerHigh else UnboundPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onConnectOrDisconnect)
                    ) {
                        Text(
                            text = if (isYouTubeConnected) "Disconnect" else "Connect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isYouTubeConnected) UnboundPrimary else OnPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }

                // 2-column dynamic status strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroStatusChip(
                        dotColor = statusDotColor,
                        text = if (isYouTubeConnected) "YouTube Synced" else "Guest Mode",
                        modifier = Modifier.weight(1f)
                    )
                    HeroStatusChip(
                        icon = Icons.Default.Headphones,
                        text = "${streamingQuality.title.substringBefore(" ")} Audio",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dotColor: Color? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(UnboundSurfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = UnboundPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== Grouped Section Primitives ====================

@Composable
private fun SettingsSectionBlock(
    title: String,
    trailingCaption: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            if (!trailingCaption.isNullOrBlank()) {
                Text(
                    text = trailingCaption,
                    fontSize = 11.5.sp,
                    color = OnSurfaceVariant
                )
            }
        }
        content()
    }
}

@Composable
private fun SettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = UnboundSurfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        thickness = 0.75.dp,
        color = UnboundSurfaceContainerHighest.copy(alpha = 0.35f)
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = UnboundPrimary,
    trailingPill: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(UnboundSurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!trailingPill.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = UnboundSurfaceContainer
            ) {
                Text(
                    text = trailingPill,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) UnboundPrimary else UnboundSurfaceContainerHighest,
        animationSpec = tween(180),
        label = "toggle_track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(180),
        label = "toggle_thumb"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = OnSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .width(44.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(trackColor)
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) OnPrimary else OnSurfaceVariant)
            )
        }
    }
}

@Composable
private fun SettingsActionButtonRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    isPrimaryButton: Boolean,
    iconTint: Color = UnboundPrimary,
    subtitleColor: Color = OnSurfaceVariant,
    showSubtitleDot: Boolean = false,
    onButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(UnboundSurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (showSubtitleDot) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(subtitleColor)
                    )
                }
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isPrimaryButton) UnboundPrimary else UnboundSurfaceContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onButtonClick)
        ) {
            Text(
                text = buttonText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPrimaryButton) OnPrimary else UnboundPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun SettingsBadgeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(UnboundSurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = UnboundPrimary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = UnboundSurfaceContainer
        ) {
            Text(
                text = badgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingsDestructiveRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val errorColor = Color(0xFFFFB4AB)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF93000A).copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = errorColor,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = errorColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = errorColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = errorColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AppFooterSection(
    localTracksCount: Int,
    onCheckUpdateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = UnboundPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Unbound Music · Version 2.0.1",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (localTracksCount > 0) {
                    "$localTracksCount local tracks indexed"
                } else {
                    "Embedded Go Engine Ready"
                },
                fontSize = 11.sp,
                color = OnSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = UnboundSurfaceContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onCheckUpdateClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Check for updates",
                    tint = UnboundPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Check for updates",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary
                )
            }
        }
    }
}

// ==================== Dialogs ====================

@Composable
private fun QualitySelectionDialog(
    title: String,
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(UnboundSurfaceContainerLow)
                .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                AudioQuality.entries.forEach { q ->
                    val isChecked = q == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isChecked) UnboundPrimary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelect(q) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isChecked,
                            onClick = { onSelect(q) },
                            colors = RadioButtonDefaults.colors(selectedColor = UnboundPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = q.title,
                                fontSize = 14.sp,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isChecked) UnboundPrimary else OnSurface
                            )
                            Text(
                                text = q.subtitle,
                                fontSize = 11.5.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
