package com.cubicreates.unboundmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest

data class MoodItem(
    val title: String,
    val imageUrl: String
)

val defaultMoods = listOf(
    MoodItem(
        title = "Chill",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDyHtO-O8nDnNQPztyPxEsNT5aiv6Ne6kZnA1tJ-cA9inwrVyr-ymBfuWGK_5T2hpME1iChe-AtYUdH5A2k0gu52Th9z-P-SGfeUJGMt0QY1siq7VZYjQXNILIioT8qz3eYxxEW89dD6F5_AItNOLnbpyL14HsmiFL-z52BXMaJHuFN93508aZZcq5yaLARGYwtG62_v-gnM094_6--dzSr_ivn2CEZqKQ4GW0i60d9WHgS5AavJJjPbw"
    ),
    MoodItem(
        title = "Workout",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAxPq4HTJcAPDJ5WTMxwDZVLLFwUiZx5DgbqsTmf0B5t4jIAUTKg-dGOVZRNbQztZubhqaz3JQbkd9CVOxQ-6rob41tJIe-X32GNdvRAzJUiFDuG0It1Q_CCJmlGjSEQ-afJLXSzgXzsWse2BvmJ3tNW-oEvStkKxl2WNLCqo_P2xiewvvc-Rq5c6PUbVBnWE9BGD0MuEIOOOthTBaWlmopATeIdm9OWCOTgJAhAsKoGClCsJoiG2TA6w"
    ),
    MoodItem(
        title = "Focus",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuC6HgRBezlfnehGhlmz0QilxBSTBtoG15Yb31LV2I8mRPr0JYREfRTqrNbum9n9W2Y8_xNQMvZ9Fvlq3fyWNEdjTfkBLpclVNpWlxaEfXRq5Wy3TGsDDcjj0DES-UJAuBNwJesZFk1QTZVYtpLgrNH1hAqMzWlxDedNxf9liGccEz9TBIvqevKFBJ7lnBaYfW5yqJOWNEeLj3rfMpAP38fCnhH3h_WE-5Kt8N9S1zB8ov1L9YcxNzLI7g"
    ),
    MoodItem(
        title = "Energy",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDKpK3reCGF7uRjNRLT8yPfymrmAvFQF8kzgYqx65G6Krz3qNKNCkKUHFFEDIg9NN-so1gOBcx2gUFVdA6V6pmjvgpckBjz8VgTXtPjdOaaOmT4wVVJqtSCq9poX75WIgSgwA_BdzIyFH6kB_UOlfQI2I1LvVoC6BiTucxhf6WBzVf_iibEw_jl0a6AtpwDU8tN8adZ-DBbJhj48AduN4fKA374KbwHZmDmK2mGOKhYp11KAqcpuc92KA"
    )
)

@Composable
fun MoodsSection(
    modifier: Modifier = Modifier,
    moods: List<MoodItem> = defaultMoods,
    onMoodClick: (MoodItem) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Moods & Moments",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            letterSpacing = (-0.01).sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(moods) { mood ->
                MoodCard(mood = mood, onClick = { onMoodClick(mood) })
            }
        }
    }
}

@Composable
private fun MoodCard(
    mood: MoodItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(128.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        // Background Artwork with dark gradient overlay
        AsyncImage(
            model = mood.imageUrl,
            contentDescription = mood.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark gradient from bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 0f,
                        endY = 400f
                    )
                )
        )

        // Title Label
        Text(
            text = mood.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            letterSpacing = (-0.01).sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}
