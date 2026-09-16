package com.cubicreates.unboundmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.TopBarGlass
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import com.cubicreates.unboundmusic.ui.components.NavigationTab

private const val DEFAULT_LOGO_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuCakI2DGdcJDf93UDif0pOaN2wJ-D8BFLf8gxIvJkzCye964IBFhswEx-awNCIJy3dzV-1LCD3nj53qsi8ax_-0BDyYsk0AkZ0Egqw9_knCCjXlKly8Ng98rokKH1ZsAMEDbn0SMS7L6eV2LsjUJvrS_E_gCLaYoB6ycOvjYm_rlgxXSJT8mPGQgf-LT2_QVLV0cZu7rd7MVl8SnoOC19M22Vv9nsSWXmnjOHPSq0ZNN5XyzeaHNqHdfrwJu3V6dEyHjOU"

/**
 * Top App Bar for Unbound Music.
 * Engineered for 100% stable relative positioning across all mobile devices.
 * Uses statusBarsPadding so the glassmorphism surface bleeds into the system
 * status bar/notch while the interactive content resides stably inside a 56dp action zone.
 */
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
                // Menu Button
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = UnboundPrimary
                    )
                }

                // Center Branding: Logo + Title (Responsive and stable)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = DEFAULT_LOGO_URL,
                        contentDescription = "Unbound Logo",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (currentTab) {
                            NavigationTab.HOME -> "Unbound"
                            NavigationTab.SEARCH -> "Discover"
                            NavigationTab.LIBRARY -> "Library"
                        },
                        color = UnboundPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).sp
                    )
                }

                // Right spacer to keep center branding centered (user icon removed as requested)
                Spacer(modifier = Modifier.size(40.dp))
            }
        }
    }
}
