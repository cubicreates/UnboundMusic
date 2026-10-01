package com.cubicreates.unboundmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.TopBarGlass
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

private const val DEFAULT_LOGO_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuCakI2DGdcJDf93UDif0pOaN2wJ-D8BFLf8gxIvJkzCye964IBFhswEx-awNCIJy3dzV-1LCD3nj53qsi8ax_-0BDyYsk0AkZ0Egqw9_knCCjXlKly8Ng98rokKH1ZsAMEDbn0SMS7L6eV2LsjUJvrS_E_gCLaYoB6ycOvjYm_rlgxXSJT8mPGQgf-LT2_QVLV0cZu7rd7MVl8SnoOC19M22Vv9nsSWXmnjOHPSq0ZNN5XyzeaHNqHdfrwJu3V6dEyHjOU"

/**
 * Top App Bar for Unbound Music.
 * Features:
 * - Left: App Logo + Contextual Screen Title
 * - Right: Active Downloads indicator (when downloading) + Interactive User Profile Avatar / Guest Button
 * - Removed redundant hamburger menu button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnboundTopAppBar(
    modifier: Modifier = Modifier,
    currentTab: NavigationTab = NavigationTab.HOME,
    userAvatarUrl: String? = null,
    accountName: String? = null,
    isLoggedIn: Boolean = false,
    activeDownloadsCount: Int = 0,
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TopBarGlass,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Branding: Logo + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = DEFAULT_LOGO_URL,
                        contentDescription = "Unbound Logo",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (currentTab) {
                            NavigationTab.HOME -> "Unbound"
                            NavigationTab.SEARCH -> "Discover"
                            NavigationTab.SHAZAM -> "Identify"
                            NavigationTab.LIBRARY -> "Library"
                        },
                        color = UnboundPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).sp
                    )
                }

                // Right Actions: Downloads Indicator + Profile Avatar / Guest Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeDownloadsCount > 0) {
                        IconButton(
                            onClick = onDownloadsClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = UnboundPrimary,
                                        contentColor = Color.Black
                                    ) {
                                        Text(text = activeDownloadsCount.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Active Downloads",
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Profile Avatar button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isLoggedIn && !userAvatarUrl.isNullOrBlank()) SurfaceGlassHighest else Color(0xFF2A2A2A))
                            .border(
                                width = 1.5.dp,
                                color = if (isLoggedIn) UnboundPrimary.copy(alpha = 0.8f) else BorderGlass,
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onProfileClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoggedIn && !userAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userAvatarUrl,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (isLoggedIn && !accountName.isNullOrBlank()) {
                            Text(
                                text = accountName.take(1).uppercase(),
                                color = UnboundPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Guest Profile",
                                tint = Color(0xFFCCCCCC),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
