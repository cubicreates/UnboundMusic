/*
 * Package: com.cubicreates.unboundmusic.ui.settings
 * File: SettingsScreen.kt
 * Purpose: Modern Studio Control & User Account Hub for Unbound Music.
 *          Features Bento User Identity Hero, Grouped Glass Setting Containers,
 *          10-Band EQ & Hardware Audio DSP, Adaptive Studio Theme Selector,
 *          Cloud Sync, Storage Maintenance, and GitHub Auto-Updates.
 * Subsystem: Settings / User Account UI
 * Concurrency: Thread-safe UI state updates.
 */

package com.cubicreates.unboundmusic.ui.settings

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.AudioQuality
import com.cubicreates.unboundmusic.ui.theme.AppThemePreset
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isYouTubeConnected: Boolean = false,
    accountName: String = "Local User",
    userAvatarUrl: String? = null,
    currentTheme: AppThemePreset = AppThemePreset.STUDIO_DARK,
    cachePurgeStatus: String? = null,
    onThemeSelected: (AppThemePreset) -> Unit = {},
    onClose: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onAutoEqClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    onSpotifyImportClick: () -> Unit = {},
    onYouTubeSyncClick: () -> Unit = {},
    onDisconnectYouTubeClick: () -> Unit = {},
    onCheckUpdateClick: () -> Unit = {},
    onPurgeCacheClick: () -> Unit = {},
    onCleanStorageForUninstallClick: () -> Unit = {},
    autoDownloadLikedSongs: Boolean = false,
    skipSilenceEnabled: Boolean = false,
    normalizeVolumeEnabled: Boolean = false,
    sponsorBlockEnabled: Boolean = true,
    streamingQuality: AudioQuality = AudioQuality.HIGH,
    downloadQuality: AudioQuality = AudioQuality.HIGH,
    onAutoDownloadLikedSongsChange: (Boolean) -> Unit = {},
    onSkipSilenceChange: (Boolean) -> Unit = {},
    onNormalizeVolumeChange: (Boolean) -> Unit = {},
    onSponsorBlockChange: (Boolean) -> Unit = {},
    onStreamingQualityChange: (AudioQuality) -> Unit = {},
    onDownloadQualityChange: (AudioQuality) -> Unit = {},
    onOpenDownloadsHub: () -> Unit = {},
    onExportBackupClick: () -> Unit = {},
    onRestoreBackupClick: () -> Unit = {}
) {
    var showStreamingQualityDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 96.dp)
        ) {
            // ==================== Header Title ====================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Studio & Preferences",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    letterSpacing = (-0.02).sp
                )
                Text(
                    text = "Account, Playback & System Settings",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            // ==================== 0. User Identity Hero Bento Card ====================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF14151C),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            if (isYouTubeConnected) UnboundPrimary.copy(alpha = 0.55f) else Color(0xFF7C4DFF).copy(alpha = 0.45f),
                            Color(0xFF262838),
                            BorderGlass
                        )
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    if (isYouTubeConnected) UnboundPrimary.copy(alpha = 0.18f) else Color(0xFF7C4DFF).copy(alpha = 0.14f),
                                    Color(0xFF161822),
                                    Color(0xFF101117)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Large Avatar with Glow Ring
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                if (isYouTubeConnected) UnboundPrimary.copy(alpha = 0.35f) else Color(0xFF7C4DFF).copy(alpha = 0.25f),
                                                Color(0xFF1F2232)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isYouTubeConnected) UnboundPrimary.copy(alpha = 0.8f) else BorderGlass,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isYouTubeConnected && !userAvatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userAvatarUrl,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (isYouTubeConnected && accountName.isNotBlank()) {
                                    Text(
                                        text = accountName.take(1).uppercase(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = UnboundPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Offline User",
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isYouTubeConnected) accountName else "Local User",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    letterSpacing = (-0.02).sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isYouTubeConnected) Color(0xFF00E676) else Color(0xFFFF9100))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isYouTubeConnected) "YouTube Synced" else "Offline Mode",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isYouTubeConnected) Color(0xFF00E676) else Color(0xFFFF9100)
                                    )
                                }
                            }

                            // Connect / Disconnect button
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isYouTubeConnected) Color(0xFFFF3B30).copy(alpha = 0.15f) else UnboundPrimary.copy(alpha = 0.18f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isYouTubeConnected) Color(0xFFFF3B30).copy(alpha = 0.40f) else UnboundPrimary.copy(alpha = 0.50f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (isYouTubeConnected) onDisconnectYouTubeClick() else onYouTubeSyncClick()
                                    }
                            ) {
                                Text(
                                    text = if (isYouTubeConnected) "Disconnect" else "Connect",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isYouTubeConnected) Color(0xFFFF5252) else UnboundPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick Status Badges Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AccountStatusPill(
                                label = "Status",
                                value = if (isYouTubeConnected) "Online" else "Local Only",
                                accentColor = if (isYouTubeConnected) Color(0xFF00E676) else Color(0xFFFF9100),
                                modifier = Modifier.weight(1f)
                            )
                            AccountStatusPill(
                                label = "Engine",
                                value = "Port 45731",
                                accentColor = Color(0xFF2979FF),
                                modifier = Modifier.weight(1f)
                            )
                            AccountStatusPill(
                                label = "Theme",
                                value = currentTheme.displayName.substringBefore(" "),
                                accentColor = currentTheme.previewPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 1. Audio & DSP Section ====================
            ModernSectionHeader(title = "AUDIO ENGINE & PRO DSP", accentColor = Color(0xFF7C4DFF))

            ModernGroupedCard {
                ModernGroupedActionRow(
                    icon = Icons.Default.Tune,
                    title = "Parametric EQ & Studio DSP",
                    subtitle = "10-Band EQ, Sub-Bass Boost, 3D Virtualizer, Loudness",
                    accentColor = Color(0xFF7C4DFF),
                    onClick = onEqualizerClick
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.Headphones,
                    title = "AutoEq Headphone Calibration",
                    subtitle = "4,000+ Harman target headphone curves",
                    accentColor = Color(0xFF2979FF),
                    onClick = onAutoEqClick
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.Bedtime,
                    title = "Sleep Timer & Fade-Out",
                    subtitle = "Smooth 30s exponential fade or stop at song end",
                    accentColor = Color(0xFF00E5FF),
                    onClick = onSleepTimerClick
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.HighQuality,
                    title = "Streaming Audio Quality",
                    subtitle = "Dynamic bitrates and cellular optimization",
                    accentColor = Color(0xFF00E676),
                    valueBadge = streamingQuality.title.substringBefore(" "),
                    onClick = { showStreamingQualityDialog = true }
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Download Audio Quality",
                    subtitle = "Offline codec and bit-depth target",
                    accentColor = Color(0xFFFF9100),
                    valueBadge = downloadQuality.title.substringBefore(" "),
                    onClick = { showDownloadQualityDialog = true }
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Downloads & Offline Storage",
                    subtitle = "Manage cached tracks, offline songs, and folders",
                    accentColor = Color(0xFFFF5252),
                    onClick = onOpenDownloadsHub
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 2. Studio Theme Engine Selector ====================
            ModernSectionHeader(title = "STUDIO THEME ENGINE", accentColor = Color(0xFFFF9100))

            ModernGroupedCard {
                Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF9100).copy(alpha = 0.16f))
                                .border(1.dp, Color(0xFFFF9100).copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color(0xFFFF9100),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Visual Palette & Accent",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                            Text(
                                text = "High-contrast dynamic themes tailored for OLED",
                                fontSize = 11.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemePreset.entries.forEach { preset ->
                            val isSelected = currentTheme == preset
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) preset.previewPrimary.copy(alpha = 0.14f) else Color(0xFF1B1B1F))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) preset.previewPrimary else Color(0xFF2E2E35),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onThemeSelected(preset) }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(preset.previewPrimary)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = preset.displayName.substringBefore(" "),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) preset.previewPrimary else OnSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 3. Playback & Automation Section ====================
            ModernSectionHeader(title = "PLAYBACK & AUTOMATION", accentColor = Color(0xFF00E676))

            ModernGroupedCard {
                ModernGroupedToggleRow(
                    icon = Icons.Default.Favorite,
                    title = "Auto-Download Liked Songs",
                    subtitle = "Automatically download favorites ready for offline playback",
                    accentColor = Color(0xFFFF5252),
                    checked = autoDownloadLikedSongs,
                    onCheckedChange = onAutoDownloadLikedSongsChange
                )

                ModernRowDivider()

                ModernGroupedToggleRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Skip Silence",
                    subtitle = "Automatically bypass silent intros and acoustic dead space",
                    accentColor = Color(0xFF00E5FF),
                    checked = skipSilenceEnabled,
                    onCheckedChange = onSkipSilenceChange
                )

                ModernRowDivider()

                ModernGroupedToggleRow(
                    icon = Icons.Default.VolumeUp,
                    title = "Loudness Normalization",
                    subtitle = "Standardize perceived volume levels across albums",
                    accentColor = Color(0xFFFF9100),
                    checked = normalizeVolumeEnabled,
                    onCheckedChange = onNormalizeVolumeChange
                )

                ModernRowDivider()

                ModernGroupedToggleRow(
                    icon = Icons.Default.Info,
                    title = "SponsorBlock Music Filter",
                    subtitle = "Skip non-music promo segments automatically",
                    accentColor = Color(0xFFB388FF),
                    checked = sponsorBlockEnabled,
                    onCheckedChange = onSponsorBlockChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 4. Services & Integration Section ====================
            ModernSectionHeader(title = "SERVICES & CLOUD", accentColor = Color(0xFF2979FF))

            ModernGroupedCard {
                ModernGroupedActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Import Spotify Playlist",
                    subtitle = "Match Spotify links against YouTube high-quality audio",
                    accentColor = Color(0xFF1DB954),
                    onClick = onSpotifyImportClick
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.Sync,
                    title = if (isYouTubeConnected) "YouTube Library Sync" else "Connect YouTube Music",
                    subtitle = if (isYouTubeConnected) "Sync Liked songs, playlists, and subscriptions" else "Ingest your cloud music collection",
                    accentColor = Color(0xFFFF3B30),
                    valueBadge = if (isYouTubeConnected) "Active" else null,
                    onClick = onYouTubeSyncClick
                )

                ModernRowDivider()

                // Background Playback & OEM Doze Exemption
                val context = androidx.compose.ui.platform.LocalContext.current
                val powerManager = remember(context) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    } else null
                }
                val isIgnoringBatteryOpt = remember(powerManager) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && powerManager != null) {
                        powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    } else true
                }

                ModernGroupedActionRow(
                    icon = Icons.Default.Bedtime,
                    title = "Background Audio & Doze Exemption",
                    subtitle = if (isIgnoringBatteryOpt) "Exempted • Continuous playback active across OEM Doze" else "Restricted • Tap to whitelist for uninterrupted playback",
                    accentColor = Color(0xFFFFD600),
                    valueBadge = if (isIgnoringBatteryOpt) "Optimized" else "Fix",
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 5. Backup & Maintenance Section ====================
            ModernSectionHeader(title = "BACKUP & SYSTEM MAINTENANCE", accentColor = Color(0xFFFF5252))

            ModernGroupedCard {
                ModernGroupedActionRow(
                    icon = Icons.Default.Backup,
                    title = "Export Backup (JSON)",
                    subtitle = "Save custom playlists, favorites, and settings to JSON",
                    accentColor = Color(0xFF2979FF),
                    onClick = onExportBackupClick
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.Restore,
                    title = "Restore from Backup (JSON)",
                    subtitle = "Restore playlists and settings from a JSON file",
                    accentColor = Color(0xFF00E676),
                    onClick = onRestoreBackupClick
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "Purge AI Models & Cache",
                    subtitle = cachePurgeStatus ?: "Safely free AI models, temp stream and lyric cache",
                    accentColor = Color(0xFFFF9100),
                    onClick = onPurgeCacheClick
                )

                ModernRowDivider()

                ModernGroupedActionRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Clean Storage & Reset",
                    subtitle = "Purge all local storage and reset filesystems",
                    accentColor = Color(0xFFFF5252),
                    onClick = onCleanStorageForUninstallClick
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 6. Engine & App Info Footer ====================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF131316),
                border = BorderStroke(1.dp, Color(0xFF24242A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unbound Music v2.4.0 (Build 240)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Embedded Go Native Engine • Port 45731",
                            fontSize = 11.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(UnboundPrimary.copy(alpha = 0.15f))
                            .border(1.dp, UnboundPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable(onClick = onCheckUpdateClick)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = "Check for Updates",
                                tint = UnboundPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Updates",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = UnboundPrimary
                            )
                        }
                    }
                }
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
    }
}

// ==================== Modern UI Components ====================

@Composable
private fun AccountStatusPill(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1D26))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ModernSectionHeader(
    title: String,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 13.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun ModernGroupedCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF161619),
        border = BorderStroke(1.dp, Color(0xFF26262B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun ModernGroupedActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    valueBadge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.16f))
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                lineHeight = 14.sp
            )
        }

        if (!valueBadge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = valueBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = Color(0xFF666666),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ModernGroupedToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.16f))
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                lineHeight = 14.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimary,
                checkedTrackColor = UnboundPrimary,
                uncheckedTrackColor = Color(0xFF2A2A2E),
                uncheckedBorderColor = Color(0xFF3E3E44)
            )
        )
    }
}

@Composable
private fun ModernRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(Color(0xFF222227))
    )
}

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
                .background(Color(0xFF161619))
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
                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                color = if (isChecked) UnboundPrimary else OnSurface
                            )
                            Text(
                                text = q.subtitle,
                                fontSize = 11.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
