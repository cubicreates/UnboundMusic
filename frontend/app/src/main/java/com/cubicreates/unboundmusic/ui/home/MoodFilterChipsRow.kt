/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: MoodFilterChipsRow.kt
 * Purpose: Horizontal scrollable mood & moment filter pills mirroring SimpMusic's home mood filters.
 * Subsystem: Home UI / Mood Filtering
 */

package com.cubicreates.unboundmusic.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

val HomeMoodFilters = listOf(
    "All",
    "Relax",
    "Sleep",
    "Energize",
    "Sad",
    "Romance",
    "Feel Good",
    "Workout",
    "Party",
    "Commute",
    "Focus"
)

@Composable
fun MoodFilterChipsRow(
    selectedMood: String = "All",
    modifier: Modifier = Modifier,
    onMoodSelected: (String) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeMoodFilters.forEach { mood ->
            val isSelected = mood.equals(selectedMood, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) UnboundPrimary else Color(0xFF1B1B1F),
                border = if (isSelected) null else BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.clickable { onMoodSelected(mood) }
            ) {
                Text(
                    text = mood,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.Black else OnSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
