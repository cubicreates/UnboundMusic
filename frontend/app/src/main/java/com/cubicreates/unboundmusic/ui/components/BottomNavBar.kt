package com.cubicreates.unboundmusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.ui.theme.BottomNavGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

enum class NavigationTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Rounded.Home, Icons.Outlined.Home),
    SEARCH("Discover", Icons.Rounded.Explore, Icons.Outlined.Explore),
    LIBRARY("Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    PROFILE("Profile", Icons.Rounded.Person, Icons.Outlined.Person)
}

@Composable
fun UnboundBottomNavBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    accountName: String? = null,
    isLoggedIn: Boolean = false,
    activeDownloadsCount: Int = 0,
    userAvatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val userInitial = accountName?.trim()?.firstOrNull()?.uppercase() ?: if (isLoggedIn) "U" else "G"
    val hasDownloads = activeDownloadsCount > 0
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color.Black.copy(alpha = 0.55f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BottomNavGlass.copy(alpha = 0.96f),
                            Color(0xFF0A0C0F).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 0.75.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTab.entries.forEach { tab ->
                val isSelected = tab == currentTab
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) UnboundPrimary else OnSurfaceVariant.copy(alpha = 0.78f),
                    animationSpec = tween(220),
                    label = "navColor_${tab.name}"
                )
                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) UnboundPrimary.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "navPill_${tab.name}"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onTabSelected(tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillBgColor)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tab == NavigationTab.PROFILE) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) UnboundPrimary.copy(alpha = 0.22f)
                                        else UnboundSurfaceContainerHigh
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        color = if (isSelected) UnboundPrimary else Color.White.copy(alpha = 0.16f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!userAvatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userAvatarUrl,
                                        contentDescription = "Profile",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = userInitial.take(1).uppercase(),
                                        color = if (isSelected) UnboundPrimary else OnSurface,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Box {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (tab == NavigationTab.LIBRARY && hasDownloads) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-1).dp)
                                            .clip(CircleShape)
                                            .background(UnboundSecondary)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = tab.label,
                        color = contentColor,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
