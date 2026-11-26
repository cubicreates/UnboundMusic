/*
 * Package: com.cubicreates.unboundmusic.ui.home.components
 * File: MediaFeedSkeleton.kt
 * Purpose: Reusable Studio Brutalist UI skeleton shared by MusicScreen and PodcastScreen,
 *          mirroring the HTML design reference layout, typography, spacing, and card hierarchy.
 * Subsystem: Home UI / Shared Design Skeleton
 */

package com.cubicreates.unboundmusic.ui.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.HomeSection
import com.cubicreates.unboundmusic.ui.components.AnimatedEqualizerBars
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLowest

data class QuickPromptSuggestion(
    val label: String,
    val query: String
)

data class CompactMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val progressFraction: Float = 0f
)

data class CuratedDeckCardData(
    val id: String,
    val tag: String,
    val title: String,
    val description: String,
    val footer: String,
    val coverUrl: String
)

data class TrendingCardData(
    val id: String,
    val rankLabel: String,
    val title: String,
    val subtitle: String,
    val metaText: String,
    val coverUrl: String,
    val isTopRank: Boolean = false
)

/**
 * Shared scrollable container with the ambient electric cyan top illumination.
 */
@Composable
fun MediaFeedScaffold(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = 36.dp,
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // Ambient Top Illumination - Electric Cyan Brutalist Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            UnboundPrimary.copy(alpha = 0.11f),
                            UnboundPrimary.copy(alpha = 0.03f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content
        )
    }
}

/**
 * Top-level segmented category switcher: Music vs Podcasts.
 */
@Composable
fun MediaSectionSwitcher(
    selectedSection: HomeSection,
    onSectionSelected: (HomeSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(top = 4.dp),
        shape = RoundedCornerShape(50),
        color = UnboundSurfaceContainerLowest,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionSwitcherButton(
                label = "Music",
                icon = Icons.Default.MusicNote,
                selected = selectedSection == HomeSection.MUSIC,
                onClick = { onSectionSelected(HomeSection.MUSIC) }
            )
            SectionSwitcherButton(
                label = "Podcasts",
                icon = Icons.Default.Podcasts,
                selected = selectedSection == HomeSection.PODCASTS,
                onClick = { onSectionSelected(HomeSection.PODCASTS) }
            )
        }
    }
}

@Composable
private fun SectionSwitcherButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) UnboundPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) OnPrimary else OnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) OnPrimary else OnSurfaceVariant,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Live Transmission badge + dynamic time-of-day greeting header.
 */
@Composable
fun TelemetryGreetingSection(
    badgeText: String,
    greetingText: String,
    subtitle: String? = null,
    isSyncing: Boolean = false,
    onSyncClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "live_dot_pulse")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(dotAlpha)
                        .clip(CircleShape)
                        .background(UnboundPrimary)
                )
                Text(
                    text = badgeText.uppercase(),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary,
                    letterSpacing = 1.2.sp
                )
            }
            Text(
                text = greetingText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onSyncClick != null) {
            Surface(
                shape = CircleShape,
                color = UnboundSurfaceContainer,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isSyncing, onClick = onSyncClick)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = UnboundPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Horizontal filter pills matching the reference Rounded-8dp glass styling.
 */
@Composable
fun RefinedFilterPillsRow(
    filters: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { label ->
            val isSelected = label.equals(selectedFilter, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) Color.White.copy(alpha = 0.12f) else UnboundSurfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) UnboundPrimary.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.06f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onFilterSelected(label) }
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) UnboundPrimary else OnSurfaceVariant,
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Shared conversational discovery card (used for Vibe AI on MusicScreen and Topic Discovery on PodcastScreen).
 */
@Composable
fun ConversationalDiscoveryCard(
    headerLabel: String,
    placeholder: String,
    quickPrompts: List<QuickPromptSuggestion>,
    isLoading: Boolean = false,
    trailingHeaderAction: (@Composable () -> Unit)? = null,
    onSubmitQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = UnboundSurfaceContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.18f)),
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle bottom-right ambient cyan glow
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 24.dp)
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(UnboundPrimary.copy(alpha = 0.08f))
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = headerLabel.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UnboundPrimary,
                            letterSpacing = 0.8.sp
                        )
                    }
                    trailingHeaderAction?.invoke()
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(UnboundSurfaceContainerLowest)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Color(0xFF737373),
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(UnboundPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val q = inputText.trim()
                                    if (q.isNotBlank()) {
                                        focusManager.clearFocus()
                                        onSubmitQuery(q)
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (inputText.isNotEmpty() && !isLoading) {
                        IconButton(
                            onClick = { inputText = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(UnboundPrimary)
                            .clickable(enabled = !isLoading) {
                                val q = inputText.trim().ifBlank { quickPrompts.firstOrNull()?.query.orEmpty() }
                                if (q.isNotBlank()) {
                                    focusManager.clearFocus()
                                    onSubmitQuery(q)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = OnPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Submit",
                                tint = OnPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (quickPrompts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickPrompts.forEach { chip ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        inputText = chip.query
                                        focusManager.clearFocus()
                                        onSubmitQuery(chip.query)
                                    }
                            ) {
                                Text(
                                    text = chip.label,
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single unified Home search bar combining text search (AI-auto-detected or normal),
 * inline Mic/Sing-a-lyric, and inline Shazam audio recognition.
 */
@Composable
fun HomeUnifiedSearchBar(
    isSearching: Boolean = false,
    isListeningAudio: Boolean = false,
    recordingSeconds: Int = 0,
    placeholder: String = "Search songs, lyrics, or artists...",
    showShazamButton: Boolean = true,
    externalQuery: String? = null,
    onQueryChange: ((String) -> Unit)? = null,
    onSubmitSearch: (String) -> Unit,
    onShazamToggle: () -> Unit = {},
    onSingLyricsClick: () -> Unit,
    onClearResults: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf(externalQuery.orEmpty()) }
    LaunchedEffect(externalQuery) {
        if (externalQuery != null && externalQuery != inputText) {
            inputText = externalQuery
        }
    }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = UnboundSurfaceContainer,
        border = BorderStroke(
            1.dp,
            if (isListeningAudio) UnboundPrimary else Color.White.copy(alpha = 0.08f)
        ),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = UnboundPrimary,
                modifier = Modifier.size(19.dp)
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = if (isListeningAudio) {
                            "Listening (${recordingSeconds}s)... Tap Shazam to finish"
                        } else {
                            placeholder
                        },
                        color = if (isListeningAudio) UnboundPrimary else OnSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { next ->
                        inputText = next
                        onQueryChange?.invoke(next)
                    },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(UnboundPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val q = inputText.trim()
                            if (q.isNotBlank()) {
                                focusManager.clearFocus()
                                onSubmitSearch(q)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (inputText.isNotEmpty() && !isSearching) {
                IconButton(
                    onClick = {
                        inputText = ""
                        onQueryChange?.invoke("")
                        onClearResults()
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Inline Voice / Sing-a-lyric button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(UnboundSurfaceContainerHigh)
                    .clickable(onClick = onSingLyricsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Sing or speak lyrics",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Inline Shazam audio identification button (Music)
            if (showShazamButton) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isListeningAudio) UnboundPrimary else UnboundPrimary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.40f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onShazamToggle)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (isListeningAudio) 10.dp else 9.dp,
                            vertical = 8.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Identify audio",
                            tint = if (isListeningAudio) OnPrimary else UnboundPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        if (isListeningAudio) {
                            Text(
                                text = "${recordingSeconds}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimary
                            )
                        }
                    }
                }
            }

            // Circular Search Submit button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(UnboundPrimary)
                    .clickable(enabled = !isSearching) {
                        val q = inputText.trim()
                        if (q.isNotBlank()) {
                            focusManager.clearFocus()
                            onSubmitSearch(q)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = OnPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Search",
                        tint = OnPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 4-row snapping horizontal infinite-column slider ("Jump Back In" / "Continue Listening"),
 * which smoothly transforms into "Results" with progressive pop-in cards during Home search or Shazam.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactJumpGridSection(
    title: String,
    items: List<CompactMediaItem>,
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    isLoadingMore: Boolean = false,
    isResultsMode: Boolean = false,
    isResultsLoading: Boolean = false,
    loadingStatusText: String = "Finding matches...",
    emptyResultsMessage: String? = null,
    actionLabel: String = "See all",
    onActionClick: (() -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    onItemClick: (CompactMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isResultsMode && items.isEmpty()) return

    val rowState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(
        lazyListState = rowState,
        snapPosition = SnapPosition.Start
    )

    val firstItemId = items.firstOrNull()?.id
    LaunchedEffect(isResultsMode, firstItemId) {
        if (rowState.firstVisibleItemIndex > 0) {
            rowState.scrollToItem(0)
        }
    }

    val firstVisibleCol by remember { derivedStateOf { rowState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleCol, items.size, isResultsMode) {
        if (!isResultsMode && onLoadMore != null && !isLoadingMore && (firstVisibleCol + 3) * 4 >= items.size) {
            onLoadMore()
        }
    }

    // Progressive staggered pop-in for Results mode
    val resultsKey = remember(isResultsMode, firstItemId, items.size) {
        "$isResultsMode:$firstItemId:${items.size}"
    }
    var revealedCount by remember(resultsKey) {
        mutableIntStateOf(if (isResultsMode) 0 else items.size)
    }
    LaunchedEffect(resultsKey) {
        if (isResultsMode && items.isNotEmpty()) {
            revealedCount = 0
            for (i in 1..items.size) {
                revealedCount = i
                kotlinx.coroutines.delay(75L)
            }
        } else {
            revealedCount = items.size
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLoadingMore || isResultsLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = UnboundPrimary,
                        strokeWidth = 2.dp
                    )
                }
            }
            if (onActionClick != null) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary,
                    modifier = Modifier.clickable(onClick = onActionClick)
                )
            }
        }

        when {
            isResultsMode && items.isEmpty() && isResultsLoading -> {
                val pulseTransition = rememberInfiniteTransition(label = "resultsSkeletonPulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0.85f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (idx in 0 until 4) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .alpha(pulseAlpha),
                            shape = RoundedCornerShape(16.dp),
                            color = UnboundSurfaceContainer,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (idx == 0) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = UnboundPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = loadingStatusText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = UnboundPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(UnboundSurfaceContainerHighest)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.58f)
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(UnboundSurfaceContainerHighest)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.36f)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(UnboundSurfaceContainerHighest.copy(alpha = 0.7f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            isResultsMode && items.isEmpty() -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = UnboundSurfaceContainer,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = emptyResultsMessage ?: "No matching songs found.",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap Clear to return to Jump Back In",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columnWidth = (maxWidth * 0.88f).coerceIn(250.dp, 320.dp)
                    LazyRow(
                        state = rowState,
                        flingBehavior = snapFlingBehavior,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val bleedPx = 20.dp.roundToPx()
                                val widerConstraints = if (constraints.hasBoundedWidth) {
                                    constraints.copy(
                                        minWidth = (constraints.minWidth + bleedPx * 2)
                                            .coerceAtMost(constraints.maxWidth + bleedPx * 2),
                                        maxWidth = constraints.maxWidth + bleedPx * 2
                                    )
                                } else {
                                    constraints
                                }
                                val placeable = measurable.measure(widerConstraints)
                                val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
                                layout(layoutWidth, placeable.height) {
                                    placeable.placeRelative(if (constraints.hasBoundedWidth) -bleedPx else 0, 0)
                                }
                            }
                    ) {
                        if (isResultsMode) {
                            val visibleItems = items.take(revealedCount.coerceAtLeast(1))
                            val resultColumns = visibleItems.chunked(4)
                            items(
                                count = resultColumns.size,
                                key = { colIndex -> "res_col_$colIndex" }
                            ) { colIndex ->
                                val colItems = resultColumns[colIndex]
                                Column(
                                    modifier = Modifier.width(columnWidth),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    colItems.forEach { item ->
                                        val isCurrent = item.id.isNotBlank() && item.id == currentTrackId
                                        CompactJumpCard(
                                            item = item,
                                            isCurrent = isCurrent,
                                            isPlaying = isPlaying,
                                            animateIn = true,
                                            onClick = { onItemClick(item) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                count = Int.MAX_VALUE,
                                key = { colIndex -> colIndex }
                            ) { colIndex ->
                                Column(
                                    modifier = Modifier.width(columnWidth),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (rowIndex in 0 until 4) {
                                        val item = items[(colIndex * 4 + rowIndex) % items.size]
                                        val isCurrent = item.id.isNotBlank() && item.id == currentTrackId
                                        CompactJumpCard(
                                            item = item,
                                            isCurrent = isCurrent,
                                            isPlaying = isPlaying,
                                            animateIn = false,
                                            onClick = { onItemClick(item) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactJumpCard(
    item: CompactMediaItem,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    animateIn: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entered by remember(item.id, animateIn) { mutableStateOf(!animateIn) }
    LaunchedEffect(item.id, animateIn) {
        if (animateIn) entered = true
    }
    val itemAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "jumpCardAlpha"
    )
    val itemOffsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 14f,
        animationSpec = tween(durationMillis = 220),
        label = "jumpCardOffsetY"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                alpha = itemAlpha
                translationY = itemOffsetY
            }
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) UnboundPrimary.copy(alpha = 0.12f) else UnboundSurfaceContainer,
        border = BorderStroke(
            1.dp,
            if (isCurrent) UnboundPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UnboundSurfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedEqualizerBars(
                                color = UnboundPrimary,
                                isPlaying = isPlaying
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.title,
                        fontSize = 13.5.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrent) UnboundPrimary else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        fontSize = 11.5.sp,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(UnboundPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (item.progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.progressFraction.coerceIn(0.05f, 1f))
                            .fillMaxHeight()
                            .background(UnboundPrimary)
                    )
                }
            }
        }
    }
}

/**
 * Horizontal deck of 256dp-wide curated cards ("Made For Your Mood" / "Featured Podcast Shows").
 */
@Composable
fun CuratedHorizontalDeckSection(
    title: String,
    subtitle: String,
    cards: List<CuratedDeckCardData>,
    actionLabel: String = "Explore",
    onActionClick: (() -> Unit)? = null,
    onCardClick: (CuratedDeckCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (onActionClick != null) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary,
                    modifier = Modifier.clickable(onClick = onActionClick)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(cards, key = { "${it.id}_${it.title}" }) { card ->
                Surface(
                    modifier = Modifier
                        .width(256.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onCardClick(card) },
                    shape = RoundedCornerShape(20.dp),
                    color = UnboundSurfaceContainer,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    shadowElevation = 6.dp
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(144.dp)
                                .background(UnboundSurfaceContainerHighest)
                        ) {
                            if (card.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = card.coverUrl,
                                    contentDescription = card.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.2f),
                                                Color.Transparent,
                                                UnboundSurfaceContainer
                                            )
                                        )
                                    )
                            )

                            // Top-left tag badge
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp),
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.70f),
                                border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = card.tag.uppercase(),
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = UnboundPrimary,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            // Bottom-right circular cyan play button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(UnboundPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = OnPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = card.title,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = card.description,
                                fontSize = 12.sp,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = card.footer,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Infinite-scrolling 1:1 square YouTube-style Mixes carousel ("Made For Your Mood").
 * Displays a 1:1 cover of the seed song with "Mix – <Song>" and "<Artists>, and more" underneath,
 * snapping card-by-card infinitely while prefetching additional tracks via onLoadMore.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouTubeMixHorizontalDeckSection(
    title: String,
    subtitle: String,
    cards: List<CuratedDeckCardData>,
    isLoadingMore: Boolean = false,
    badgeLabel: String = "Mix",
    actionLabel: String = "Shuffle Mix",
    onActionClick: (() -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    onCardClick: (CuratedDeckCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return

    val rowState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(
        lazyListState = rowState,
        snapPosition = SnapPosition.Start
    )

    val firstCardId = cards.firstOrNull()?.id
    LaunchedEffect(firstCardId) {
        if (rowState.firstVisibleItemIndex > 0) {
            rowState.scrollToItem(0)
        }
    }

    val firstVisibleIdx by remember { derivedStateOf { rowState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIdx, cards.size) {
        if (onLoadMore != null && !isLoadingMore && firstVisibleIdx + 6 >= cards.size) {
            onLoadMore()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                    if (isLoadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = UnboundPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (onActionClick != null) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary,
                    modifier = Modifier.clickable(onClick = onActionClick)
                )
            }
        }

        LazyRow(
            state = rowState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val bleedPx = 20.dp.roundToPx()
                    val widerConstraints = if (constraints.hasBoundedWidth) {
                        constraints.copy(
                            minWidth = (constraints.minWidth + bleedPx * 2)
                                .coerceAtMost(constraints.maxWidth + bleedPx * 2),
                            maxWidth = constraints.maxWidth + bleedPx * 2
                        )
                    } else {
                        constraints
                    }
                    val placeable = measurable.measure(widerConstraints)
                    val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
                    layout(layoutWidth, placeable.height) {
                        placeable.placeRelative(if (constraints.hasBoundedWidth) -bleedPx else 0, 0)
                    }
                }
        ) {
            items(
                count = Int.MAX_VALUE,
                key = { index -> index }
            ) { index ->
                val card = cards[index % cards.size]
                Column(
                    modifier = Modifier
                        .width(162.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onCardClick(card) }
                ) {
                    // 1:1 Square Cover of the Seed Song
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(UnboundSurfaceContainerHighest)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.07f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (card.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = card.coverUrl,
                                contentDescription = card.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = UnboundPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.78f)
                                        )
                                    )
                                )
                        )

                        // Bottom-left Mix badge
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.68f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (badgeLabel.equals("Show", true)) Icons.Default.Podcasts else Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = badgeLabel,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Bottom-right circular Play button
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(UnboundPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Mix",
                                tint = OnPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = card.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.5.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = card.description,
                        fontSize = 11.5.sp,
                        color = OnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Infinite-swiping horizontal chart carousel ("Trending Globally" & "Trending Regionally").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrendingHorizontalCarouselSection(
    title: String,
    subtitle: String,
    items: List<TrendingCardData>,
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    accentColor: Color = UnboundPrimary,
    actionLabel: String = "Play All",
    onActionClick: (() -> Unit)? = null,
    onItemClick: (TrendingCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val rowState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(
        lazyListState = rowState,
        snapPosition = SnapPosition.Start
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (onActionClick != null) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.clickable(onClick = onActionClick)
                )
            }
        }

        LazyRow(
            state = rowState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val bleedPx = 20.dp.roundToPx()
                    val widerConstraints = if (constraints.hasBoundedWidth) {
                        constraints.copy(
                            minWidth = (constraints.minWidth + bleedPx * 2)
                                .coerceAtMost(constraints.maxWidth + bleedPx * 2),
                            maxWidth = constraints.maxWidth + bleedPx * 2
                        )
                    } else {
                        constraints
                    }
                    val placeable = measurable.measure(widerConstraints)
                    val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
                    layout(layoutWidth, placeable.height) {
                        placeable.placeRelative(if (constraints.hasBoundedWidth) -bleedPx else 0, 0)
                    }
                }
        ) {
            items(
                count = Int.MAX_VALUE,
                key = { index -> index }
            ) { index ->
                val item = items[index % items.size]
                val isCurrent = item.id.isNotBlank() && item.id == currentTrackId
                Surface(
                    modifier = Modifier
                        .width(166.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onItemClick(item) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isCurrent) accentColor.copy(alpha = 0.12f) else UnboundSurfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) accentColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f)
                    )
                ) {
                    Column(modifier = Modifier.padding(11.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(UnboundSurfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = item.coverUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedEqualizerBars(
                                        color = accentColor,
                                        isPlaying = isPlaying
                                    )
                                }
                            }

                            // Top-left rank badge
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp),
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.80f),
                                border = BorderStroke(
                                    1.dp,
                                    if (item.isTopRank) accentColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.20f)
                                )
                            ) {
                                Text(
                                    text = item.rankLabel,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isTopRank) accentColor else Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            // Bottom-right circular play button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = OnPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = item.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCurrent) accentColor else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.metaText,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2-column square card grid ("Trending Today" / "Top Episodes Today").
 */
@Composable
fun TrendingTwoColumnSection(
    title: String,
    badgeLabel: String,
    items: List<TrendingCardData>,
    onItemClick: (TrendingCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = badgeLabel,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.5.sp,
                color = OnSurfaceVariant
            )
        }

        val rows = items.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { item ->
                        TrendingSquareCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingSquareCard(
    item: TrendingCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = UnboundSurfaceContainer,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UnboundSurfaceContainerHighest)
            ) {
                if (item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top-left rank badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.80f),
                    border = BorderStroke(
                        1.dp,
                        if (item.isTopRank) UnboundPrimary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.20f)
                    )
                ) {
                    Text(
                        text = item.rankLabel,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isTopRank) UnboundPrimary else Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Bottom-right circular cyan play button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(UnboundPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = OnPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                fontSize = 12.sp,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.metaText,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
