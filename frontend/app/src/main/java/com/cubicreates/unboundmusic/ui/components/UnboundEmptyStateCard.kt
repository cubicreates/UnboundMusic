/*
 * Package: com.cubicreates.unboundmusic.ui.components
 * File: UnboundEmptyStateCard.kt
 * Purpose: Unified, clean empty/null-state card reused across screens, fragments, lists, and modals.
 * Subsystem: Shared UI Components
 */

package com.cubicreates.unboundmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLow

@Composable
fun UnboundEmptyStateCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Default.MusicOff,
    iconTint: Color = UnboundPrimary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = UnboundSurfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = if (compact) 18.dp else 28.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 40.dp else 48.dp)
                    .clip(CircleShape)
                    .background(UnboundSurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint.copy(alpha = 0.85f),
                    modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                )
            }

            Text(
                text = title,
                fontSize = if (compact) 14.5.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                textAlign = TextAlign.Center
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = OnSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }

            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = UnboundPrimary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onAction)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
