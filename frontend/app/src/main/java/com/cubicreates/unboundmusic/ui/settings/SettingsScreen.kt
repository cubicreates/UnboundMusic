/*
 * Package: com.cubicreates.unboundmusic.ui.settings
 * File: SettingsScreen.kt
 * Purpose: Studio Control Hub for Unbound Music.
 *          Manages Google/YouTube Identity banner, 10-Band EQ & Hardware Audio DSP,
 *          Adaptive Studio Theme selector, Spotify Importer, Storage Cache Purge, and GitHub Auto-Updates.
 * Subsystem: Settings / System Controls UI
 * Concurrency: Thread-safe UI state updates.
 */

package com.cubicreates.unboundmusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    onPurgeCacheClick: () -> Unit = {}
) {
    var discordRpcEnabled by remember { mutableStateOf(true) }
    var sponsorBlockEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 40.dp)
        ) {
            // Header Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OnSurface
                    )
                }

                Text(
                    text = "Studio Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )

                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 0. User Identity & Google Studio Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlassHighest)
                    .border(
                        width = 1.dp,
                        color = if (isYouTubeConnected) UnboundPrimary.copy(alpha = 0.5f) else BorderGlass,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isYouTubeConnected) UnboundPrimary else UnboundSurfaceContainerHigh),
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
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Offline User",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isYouTubeConnected) accountName else "Local Offline Studio",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isYouTubeConnected) "YouTube Account Connected • Synced" else "Offline mode • Local audio playback",
                            fontSize = 12.sp,
                            color = if (isYouTubeConnected) UnboundPrimary else OnSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isYouTubeConnected) Color(0x33FF3B30) else UnboundPrimary.copy(alpha = 0.2f))
                            .clickable {
                                if (isYouTubeConnected) onDisconnectYouTubeClick() else onYouTubeSyncClick()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isYouTubeConnected) "Disconnect" else "Connect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isYouTubeConnected) Color(0xFFFF453A) else UnboundPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Audio & DSP Section
            SettingsSectionHeader(title = "AUDIO & PRO DSP")

            SettingsActionTile(
                icon = Icons.Default.Tune,
                title = "Parametric EQ & Studio DSP",
                subtitle = "10-Band EQ, Sub-Bass Boost, 3D Virtualizer, Loudness",
                onClick = onEqualizerClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsActionTile(
                icon = Icons.Default.Headphones,
                title = "AutoEq Headphone Calibration",
                subtitle = "4,000+ Harman target headphone calibration curves",
                onClick = onAutoEqClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsActionTile(
                icon = Icons.Default.Bedtime,
                title = "Sleep Timer & Fade-Out",
                subtitle = "Smooth 30s exponential fade attenuation",
                onClick = onSleepTimerClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Studio Theme Engine Selector
            SettingsSectionHeader(title = "STUDIO THEME ENGINE")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Visual Aesthetic",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemePreset.entries.forEach { preset ->
                        val isSelected = currentTheme == preset
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) UnboundSurfaceContainerHigh else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) preset.previewPrimary else BorderGlass,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onThemeSelected(preset) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(preset.previewPrimary)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = preset.displayName.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) OnSurface else OnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Integration & Services Section
            SettingsSectionHeader(title = "SERVICES & INTEGRATIONS")

            SettingsActionTile(
                icon = Icons.Default.FileDownload,
                title = "Import Spotify Playlist",
                subtitle = "Match Spotify links against YouTube Opus audio",
                onClick = onSpotifyImportClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsActionTile(
                icon = Icons.Default.Sync,
                title = if (isYouTubeConnected) "YouTube Library Sync" else "Connect YouTube Music",
                subtitle = if (isYouTubeConnected) "Sync Liked songs, playlists, subscriptions" else "Ingest your cloud music collection",
                onClick = onYouTubeSyncClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleTile(
                icon = Icons.Default.Radio,
                title = "Discord Rich Presence",
                subtitle = "Broadcast listening status on Discord",
                checked = discordRpcEnabled,
                onCheckedChange = { discordRpcEnabled = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleTile(
                icon = Icons.Default.Info,
                title = "SponsorBlock Audio Filter",
                subtitle = "Skip non-music segments automatically",
                checked = sponsorBlockEnabled,
                onCheckedChange = { sponsorBlockEnabled = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Storage & Maintenance Section
            SettingsSectionHeader(title = "STORAGE & MAINTENANCE")

            SettingsActionTile(
                icon = Icons.Default.DeleteSweep,
                title = "Purge Stream & Lyrics Cache",
                subtitle = cachePurgeStatus ?: "Safely free temp streaming audio and lyric cache",
                onClick = onPurgeCacheClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsActionTile(
                icon = Icons.Default.Update,
                title = "Check for Updates",
                subtitle = "Direct in-app GitHub releases update checker",
                onClick = onCheckUpdateClick
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Engine Version Footnote
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Unbound Music v2.4.0 (Build 240)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "Embedded Go Native Engine • Port 45731",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurfaceVariant,
        letterSpacing = 0.1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(UnboundSurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = UnboundPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(UnboundSurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = UnboundPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = OnSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimary,
                checkedTrackColor = UnboundPrimary,
                uncheckedTrackColor = UnboundSurfaceContainerHigh
            )
        )
    }
}
