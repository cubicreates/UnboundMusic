/*
 * Package: com.cubicreates.unboundmusic.ui.components
 * File: UnboundTrackThumbnail.kt
 * Purpose: Robust, beautiful track/playlist thumbnail with automatic fallback to the Unbound Music symbol.
 * Subsystem: UI Components
 */

package com.cubicreates.unboundmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

@Composable
fun UnboundTrackThumbnail(
    coverUrl: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    iconSize: Dp = 22.dp,
    fallbackIcon: ImageVector = Icons.Default.MusicNote,
    fallbackTint: Color = UnboundPrimary,
    showGradientPlaceholder: Boolean = true
) {
    var hasError by remember(coverUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(UnboundSurfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl.isNotBlank() && !hasError) {
            AsyncImage(
                model = coverUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { hasError = true }
            )
        } else {
            // Elegant placeholder: Dark gradient with Unbound primary music symbol
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showGradientPlaceholder) {
                            Modifier.background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF2A2B3A),
                                        Color(0xFF1B1B24)
                                    )
                                )
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = contentDescription,
                    tint = fallbackTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
