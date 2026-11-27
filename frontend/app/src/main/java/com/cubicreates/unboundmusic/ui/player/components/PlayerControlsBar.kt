/*
 * Package: com.cubicreates.unboundmusic.ui.player.components
 * File: PlayerControlsBar.kt
 * Purpose: Interactive 5-button playback deck with spring press physics,
 *          Electric Cyan Play/Pause hero circle, and active indicator dots for shuffle & repeat.
 * Subsystem: Player UI Component
 * Concurrency: Stateless composable.
 */

package com.cubicreates.unboundmusic.ui.player.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

@Composable
fun PlayerControlsBar(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    isShuffle: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    repeatMode: Int = 0, // 0 = off, 1 = all, 2 = one
    onToggleRepeat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Shuffle with active cyan indicator dot
        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) UnboundPrimary else OnSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (isShuffle) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(UnboundPrimary)
                    )
                }
            }
        }

        // 2. Previous
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = OnSurface,
                modifier = Modifier.size(34.dp)
            )
        }

        // 3. Electric Cyan Play / Pause Hero Button (Animated Spring Press)
        val playInteractionSource = remember { MutableInteractionSource() }
        val isPressed by playInteractionSource.collectIsPressedAsState()
        val playScale by animateFloatAsState(
            targetValue = if (isPressed) 0.90f else 1.0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
            label = "playScale"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .scale(playScale)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = UnboundPrimary.copy(alpha = 0.45f),
                    spotColor = UnboundPrimary.copy(alpha = 0.60f)
                )
                .clip(CircleShape)
                .background(UnboundPrimary)
                .clickable(
                    interactionSource = playInteractionSource,
                    indication = null,
                    onClick = onPlayPause
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = OnPrimary,
                modifier = Modifier.size(34.dp)
            )
        }

        // 4. Next
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = OnSurface,
                modifier = Modifier.size(34.dp)
            )
        }

        // 5. Repeat with active cyan indicator dot
        IconButton(onClick = onToggleRepeat, modifier = Modifier.size(44.dp)) {
            val repeatIcon = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
            val isRepeatActive = repeatMode > 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = if (isRepeatActive) UnboundPrimary else OnSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (isRepeatActive) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(UnboundPrimary)
                    )
                }
            }
        }
    }
}
