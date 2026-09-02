package com.cubicreates.unboundmusic.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.BottomNavGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

enum class NavigationTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", Icons.Default.Favorite),
    PLAYING("Playing", Icons.Default.PlayArrow)
}

@Composable
fun UnboundBottomNavBar(
    modifier: Modifier = Modifier,
    currentTab: NavigationTab = NavigationTab.HOME,
    onTabSelected: (NavigationTab) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(BottomNavGlass)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .navigationBarsPadding()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTab.values().forEach { tab ->
                val isSelected = tab == currentTab
                val contentColor = if (isSelected) UnboundPrimary else OnSurfaceVariant.copy(alpha = 0.6f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(tab) }
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(UnboundPrimary)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier.size(if (isSelected) 26.dp else 22.dp)
                    )
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor
                    )
                }
            }
        }
    }
}
