/*
 * Package: com.cubicreates.unboundmusic.ui.components
 * File: PermissionsOnboardingSheet.kt
 * Purpose: Studio Brutalist One-Tap Permissions Onboarding modal. Bundles Media,
 *          Notifications, Microphone (Shazam), Battery Optimization, and All Files Access
 *          into a single seamless startup flow so users are never interrupted during playback.
 * Subsystem: Onboarding & Permissions Lifecycle
 */

package com.cubicreates.unboundmusic.ui.components

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

data class PermissionItemState(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean
)

@Composable
fun PermissionsOnboardingSheet(
    audioGranted: Boolean,
    notificationsGranted: Boolean,
    microphoneGranted: Boolean,
    batteryOptimized: Boolean,
    allFilesGranted: Boolean,
    onGrantAllClicked: () -> Unit,
    onRequestAllFilesClicked: () -> Unit,
    onDismissOrSkip: () -> Unit
) {
    val allCoreGranted = audioGranted && notificationsGranted && microphoneGranted

    Dialog(
        onDismissRequest = onDismissOrSkip,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F1215))
                .border(1.dp, Color(0xFF26333D), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Technical Brutalist Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF13222B))
                        .border(1.dp, UnboundPrimary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ONE-TAP SETUP",
                        color = UnboundPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Welcome to Unbound Music",
                    color = OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Grant permissions once to unlock uninterrupted offline & streaming playback with zero surprise popups.",
                    color = OnSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Permission Rows
                PermissionCard(
                    title = "Audio Library & Downloads",
                    subtitle = "Scan and play your offline songs & files",
                    icon = Icons.Default.MusicNote,
                    isGranted = audioGranted
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionCard(
                    title = "Lock Screen & Notification Controls",
                    subtitle = "Artwork on lock screen & background player bar",
                    icon = Icons.Default.Notifications,
                    isGranted = notificationsGranted
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionCard(
                    title = "Song Recognition (Shazam)",
                    subtitle = "Identify songs playing around you instantly",
                    icon = Icons.Default.Mic,
                    isGranted = microphoneGranted
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionCard(
                    title = "Unrestricted Battery Playback",
                    subtitle = "Stops Android from stopping music when screen is off",
                    icon = Icons.Default.BatteryChargingFull,
                    isGranted = batteryOptimized
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PermissionCard(
                        title = "VLC Deep Storage Access",
                        subtitle = "Find songs in custom folders, Telegram & WhatsApp",
                        icon = Icons.Default.Folder,
                        isGranted = allFilesGranted,
                        onRowClick = if (!allFilesGranted) onRequestAllFilesClicked else null
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Big One-Tap Button
                Button(
                    onClick = {
                        if (allCoreGranted) {
                            onDismissOrSkip()
                        } else {
                            onGrantAllClicked()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UnboundPrimary,
                        contentColor = OnPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (allCoreGranted) Icons.Default.Check else Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (allCoreGranted) "START LISTENING" else "GRANT ALL PERMISSIONS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (!allCoreGranted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDismissOrSkip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = OnSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26333D)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "MAYBE LATER",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGranted: Boolean,
    onRowClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onRowClick != null) Modifier.clickable { onRowClick() } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141A1F))
            .border(
                width = 1.dp,
                color = if (isGranted) Color(0xFF1C3A2B) else Color(0xFF26333D),
                shape = RoundedCornerShape(8.dp)
            )
            .then(clickableModifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isGranted) Color(0xFF0F2B1D) else Color(0xFF19232B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF4EE1A0) else UnboundPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = subtitle,
                color = OnSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isGranted) Color(0xFF143826) else Color(0xFF26333D))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isGranted) "ALLOWED" else if (onRowClick != null) "TAP TO ENABLE" else "REQUIRED",
                color = if (isGranted) Color(0xFF4EE1A0) else Color(0xFFB0C4D0),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
