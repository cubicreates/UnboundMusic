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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private const val DEFAULT_LOGO_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuCakI2DGdcJDf93UDif0pOaN2wJ-D8BFLf8gxIvJkzCye964IBFhswEx-awNCIJy3dzV-1LCD3nj53qsi8ax_-0BDyYsk0AkZ0Egqw9_knCCjXlKly8Ng98rokKH1ZsAMEDbn0SMS7L6eV2LsjUJvrS_E_gCLaYoB6ycOvjYm_rlgxXSJT8mPGQgf-LT2_QVLV0cZu7rd7MVl8SnoOC19M22Vv9nsSWXmnjOHPSq0ZNN5XyzeaHNqHdfrwJu3V6dEyHjOU"

@Composable
fun UnboundTopAppBar(
    modifier: Modifier = Modifier,
    userAvatarUrl: String? = null,
    accountName: String? = null,
    isLoggedIn: Boolean = false,
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(TopBarGlass)
            .border(width = 1.dp, color = BorderGlass)
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

            // Center Branding: Logo + Unbound Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = DEFAULT_LOGO_URL,
                    contentDescription = "Unbound Logo",
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unbound",
                    color = UnboundPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.03).sp
                )
            }

            // User Avatar / Profile Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
                    .border(
                        width = 1.dp,
                        color = if (isLoggedIn) UnboundPrimary.copy(alpha = 0.6f) else BorderGlass,
                        shape = CircleShape
                    )
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                if (isLoggedIn && !userAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = userAvatarUrl,
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (isLoggedIn && !accountName.isNullOrBlank()) {
                    Text(
                        text = accountName.take(1).uppercase(),
                        color = UnboundPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Sign In / Profile",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
