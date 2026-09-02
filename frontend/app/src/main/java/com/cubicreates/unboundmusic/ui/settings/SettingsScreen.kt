/*
 * Package: com.cubicreates.unboundmusic.ui.settings
 * File: SettingsScreen.kt
 * Purpose: Comprehensive Settings & Utilities Hub for Unbound Music.
 *          Manages 10-Band EQ, Sleep Timer with fade-out, Last.fm scrobbler, Discord Rich Presence,
 *          Storage Rules, Spotify Importer, YouTube Account Sync, and GitHub Auto-Updates.
 * Subsystem: Settings / System Controls UI
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onAutoEqClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    onSpotifyImportClick: () -> Unit = {},
    onYouTubeSyncClick: () -> Unit = {},
    onCheckUpdateClick: () -> Unit = {},
    onClearCacheClick: () -> Unit = {}
) {
    var discordRpcEnabled by remember { mutableStateOf(true) }
    var sponsorBlockEnabled by remember { mutableStateOf(true) }
    var highResAudioEnabled by remember { mutableStateOf(true) }

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
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )

                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Audio & DSP Section
            SettingsSectionHeader(title = "AUDIO & DSP")

            SettingsActionTile(
                icon = Icons.Default.Tune,
                title = "10-Band Parametric Equalizer",
                subtitle = "Custom biquad frequency curves & preamp gain",
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
                subtitle = "30s smooth exponential fade attenuation",
                onClick = onSleepTimerClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Integration & Scrobbling Section
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
                title = "YouTube Account Sync",
                subtitle = "Sync liked songs, subscriptions & custom playlists",
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
                subtitle = "Skip non-music intro/outro segments automatically",
                checked = sponsorBlockEnabled,
                onCheckedChange = { sponsorBlockEnabled = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Storage & Maintenance Section
            SettingsSectionHeader(title = "STORAGE & MAINTENANCE")

            SettingsActionTile(
                icon = Icons.Default.DeleteSweep,
                title = "Clear Audio Cache",
                subtitle = "Free temporary streaming buffers without touching indexed tracks",
                onClick = onClearCacheClick
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
