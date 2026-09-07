/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: MoodAndGenreBoard.kt
 * Purpose: Zero-lag technical brutalist genre and mood exploration boards.
 *          Renders zero-image lightweight cards with deterministic ARGB left stripes
 *          for smooth 120 FPS scrolling without GPU overdraw or recomposition flickering.
 * Subsystem: Home UI / Discovery Boards
 * Concurrency: Thread-safe Compose rendering.
 */

package com.cubicreates.unboundmusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.GenreItemDto
import com.cubicreates.unboundmusic.data.GenreSectionDto
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

private val FALLBACK_PALETTE = listOf(
    Color(0xFF4CD6FB), // Unbound Cyan
    Color(0xFFD67BFF), // Neon Violet
    Color(0xFF00FF66), // Matrix Emerald
    Color(0xFFFFB74D), // Amber
    Color(0xFFFF5722), // Sunset Orange
    Color(0xFF9C27B0), // Deep Purple
    Color(0xFF00E676), // Mint
    Color(0xFF2979FF)  // Electric Blue
)

/**
 * Resolves deterministic ARGB stripe color from API or title hash.
 */
fun resolveStripeColor(item: GenreItemDto): Color {
    if (item.stripeColor != 0L) {
        val argb = item.stripeColor.toInt()
        // Ensure alpha is fully opaque if provided as RGB
        val finalArgb = if ((argb ushr 24) == 0) (0xFF shl 24) or (argb and 0x00FFFFFF) else argb
        return Color(finalArgb)
    }
    val hash = item.title.hashCode()
    val index = (hash and 0x7FFFFFFF) % FALLBACK_PALETTE.size
    return FALLBACK_PALETTE[index]
}

@Composable
fun MoodAndGenreBoard(
    sections: List<GenreSectionDto>,
    modifier: Modifier = Modifier,
    onGenreClick: (GenreItemDto) -> Unit = {}
) {
    if (sections.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        sections.forEach { section ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section.title.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "EXPLORE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UnboundPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(section.items, key = { it.title + it.params }) { item ->
                        BrutalistGenreCard(
                            item = item,
                            onClick = { onGenreClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrutalistGenreCard(
    item: GenreItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stripeColor = remember(item.title, item.stripeColor) {
        resolveStripeColor(item)
    }

    Box(
        modifier = modifier
            .width(148.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161616))
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 4dp Left Color Accent Stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Bold Technical Typography
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
