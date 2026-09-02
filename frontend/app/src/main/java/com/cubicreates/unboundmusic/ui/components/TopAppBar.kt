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
import com.cubicreates.unboundmusic.ui.theme.TopBarGlass
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

private const val DEFAULT_LOGO_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuCakI2DGdcJDf93UDif0pOaN2wJ-D8BFLf8gxIvJkzCye964IBFhswEx-awNCIJy3dzV-1LCD3nj53qsi8ax_-0BDyYsk0AkZ0Egqw9_knCCjXlKly8Ng98rokKH1ZsAMEDbn0SMS7L6eV2LsjUJvrS_E_gCLaYoB6ycOvjYm_rlgxXSJT8mPGQgf-LT2_QVLV0cZu7rd7MVl8SnoOC19M22Vv9nsSWXmnjOHPSq0ZNN5XyzeaHNqHdfrwJu3V6dEyHjOU"
private const val DEFAULT_AVATAR_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuDcTQQ1bl8ZxvPmgQ0g_aYec8mLe-4w7tCWeMr5L_optqPehpTSVv0oBsQF7_3CZXsgRR0RG_7ihLu4ZXRHrXFPmYTVQjeRpnqjpv-3GdFlKUdC1ZuZArnEQDQQTDsbfZg-_LilhnLyNM0se-g-cJJngxZwOUZ2E0rkfq86e6bJNiP7VpzaHjbLHTjxQsmVO_awHa9c9KG_uYotSDXn8D_2uyIgeJJEEcx4xhlNmGLUnLbjqMYkEn_oCw"

@Composable
fun UnboundTopAppBar(
    modifier: Modifier = Modifier,
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

            // User Avatar Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                    .clickable(onClick = onProfileClick)
            ) {
                AsyncImage(
                    model = DEFAULT_AVATAR_URL,
                    contentDescription = "User Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
