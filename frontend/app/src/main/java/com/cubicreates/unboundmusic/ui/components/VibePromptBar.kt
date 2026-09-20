/*
 * Package: com.cubicreates.unboundmusic.ui.components
 * File: VibePromptBar.kt
 * Purpose: Conversational natural language prompt bar that allows users to express moods/vibes
 *          (e.g., "Hey I am feeling Sad play some music") and immediately initiates contextual playback.
 * Subsystem: UI / Discovery & Affective MIR
 */

package com.cubicreates.unboundmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary
import kotlinx.coroutines.delay

import androidx.compose.ui.platform.LocalContext
import com.cubicreates.unboundmusic.util.GeoLocationProvider

private val suggestedPromptsGlobal = listOf(
    "Hey I am feeling Sad play some music",
    "Heavy workout motivation at the gym",
    "Late night rainy highway drive",
    "Relaxing lofi beats for deep focus",
    "Upbeat feel-good summer party songs",
    "Melancholy acoustic heartbreak ballads"
)

private val suggestedPromptsIndian = listOf(
    "Victory Songs",
    "Hey I am feeling Sad play some music",
    "High energy gym workout Bollywood Punjabi",
    "Late night drive acoustic Hindi indie",
    "Peaceful Sufi acoustic soul songs",
    "Heartbroken romantic Hindi ballads"
)

data class QuickVibeChip(
    val label: String,
    val prompt: String,
    val iconEmoji: String
)

private val quickVibeChipsIndian = listOf(
    QuickVibeChip("Victory", "Victory Songs", "🏆"),
    QuickVibeChip("Sadness", "Hey I am feeling Sad play some music", "🌧"),
    QuickVibeChip("Desi Hype", "High energy gym workout Bollywood Punjabi", "⚡"),
    QuickVibeChip("Sufi Soul", "Peaceful Sufi acoustic soul songs", "🕊"),
    QuickVibeChip("Romance", "Heartbroken romantic Hindi ballads", "❤️"),
    QuickVibeChip("Night Drive", "Late night drive acoustic Hindi indie", "🌙"),
    QuickVibeChip("Lo-Fi Chill", "Lofi chill beats to relax", "☕")
)

private val quickVibeChipsGlobal = listOf(
    QuickVibeChip("Victory", "Victory Songs", "🏆"),
    QuickVibeChip("Sadness", "Hey I am feeling Sad play some music", "🌧"),
    QuickVibeChip("Gym Hype", "Heavy workout gym motivation music", "⚡"),
    QuickVibeChip("Night Drive", "Late night highway drive synthwave", "🌙"),
    QuickVibeChip("Lo-Fi Chill", "Lofi chill beats to relax", "☕"),
    QuickVibeChip("Deep Focus", "Study beats deep focus music", "🧠"),
    QuickVibeChip("Party", "Upbeat feel good dance party hits", "🎉")
)

@Composable
fun VibeAIStatusCard(
    modifier: Modifier = Modifier,
    isOfflineReady: Boolean = true,
    statusText: String = "Vibe AI Engine: Offline Ready (SmolLM2 135M Active)"
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141414),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOfflineReady) Color(0xFF00E676) else UnboundPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = UnboundPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
fun VibePromptBar(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onVibeSubmit: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val userCountry = remember(context) { GeoLocationProvider.getCountryCode(context) }
    val suggestedPrompts = remember(userCountry) {
        if (userCountry.equals("IN", ignoreCase = true)) suggestedPromptsIndian else suggestedPromptsGlobal
    }
    val quickVibeChips = remember(userCountry) {
        if (userCountry.equals("IN", ignoreCase = true)) quickVibeChipsIndian else quickVibeChipsGlobal
    }
    var promptIndex by remember { mutableIntStateOf(0) }

    // Cycle hint examples every 4 seconds when input is blank
    LaunchedEffect(textInput, suggestedPrompts) {
        if (textInput.isBlank()) {
            while (true) {
                delay(4000)
                promptIndex = (promptIndex + 1) % suggestedPrompts.size
            }
        }
    }

    // Subtle pulsing border animation
    val infiniteTransition = rememberInfiniteTransition(label = "vibe_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Main Input Pill Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .border(
                    BorderStroke(
                        1.2.dp,
                        if (isLoading) UnboundPrimary else UnboundPrimary.copy(alpha = glowAlpha)
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sparkle AI Icon with gradient aura
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    UnboundPrimary.copy(alpha = 0.25f),
                                    UnboundTertiary.copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Vibe AI",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Text Input Area with cycling placeholder
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (textInput.isEmpty()) {
                        Text(
                            text = suggestedPrompts[promptIndex],
                            color = OnSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 13.5.sp,
                            maxLines = 1
                        )
                    }

                    BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        textStyle = TextStyle(
                            color = OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(UnboundPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val query = textInput.trim().ifBlank { suggestedPrompts[promptIndex] }
                                focusManager.clearFocus()
                                onVibeSubmit(query)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Trailing Action: Loading / Clear / Send
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(2.dp),
                        strokeWidth = 2.dp,
                        color = UnboundPrimary
                    )
                } else if (textInput.isNotEmpty()) {
                    IconButton(
                        onClick = { textInput = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val query = textInput.trim()
                            if (query.isNotBlank()) {
                                focusManager.clearFocus()
                                onVibeSubmit(query)
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(UnboundPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Play Vibe",
                            tint = OnPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val query = suggestedPrompts[promptIndex]
                            focusManager.clearFocus()
                            onVibeSubmit(query)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(UnboundPrimary.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Play Suggestion",
                            tint = UnboundPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Vibe Chips Horizontal Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickVibeChips.forEach { chip ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(0.8.dp, Color(0xFF333333)),
                    modifier = Modifier.clickable {
                        textInput = chip.prompt
                        focusManager.clearFocus()
                        onVibeSubmit(chip.prompt)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = chip.iconEmoji,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = chip.label,
                            color = OnSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
