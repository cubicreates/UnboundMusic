/*
 * Package: com.cubicreates.unboundmusic.ui.equalizer
 * File: EqualizerScreen.kt
 * Purpose: Pro Audio Studio Equalizer & DSP Hub with 2 main tabs:
 *          Tab 1 - Equalizer: EQ power toggle, 5-band vertical sliders (-15dB to +15dB),
 *                  genre/effect presets dropdown, and neon rotary knobs for Bass Booster & Virtualizer.
 *          Tab 2 - Volume: System stream volume slider, Amplifier (loudness boost),
 *                  reverb environment chips (Small Room, Medium Hall, etc.), and sound balance.
 * Subsystem: Pro Audio DSP / Equalizer UI
 */

package com.cubicreates.unboundmusic.ui.equalizer

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.data.UserEqPresetDto
import com.cubicreates.unboundmusic.ui.components.RotaryKnob
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

private val FIVE_BAND_LABELS = listOf("60HZ", "230HZ", "910HZ", "1.4KHZ", "3.6KHZ")

private val BUILT_IN_EFFECT_PRESETS = listOf(
    "Normal" to listOf(0f, 0f, 0f, 0f, 0f),
    "Classical" to listOf(4.5f, 3.0f, -1.5f, 2.5f, 3.5f),
    "Dance" to listOf(6.0f, 2.5f, 2.0f, 4.0f, 4.5f),
    "Straightness" to listOf(0f, 0f, 0f, 0f, 0f),
    "Folk" to listOf(3.0f, 1.5f, 0f, 2.0f, -1.0f),
    "Heavy Metal" to listOf(4.0f, 1.0f, 7.0f, 3.0f, 0f),
    "Hip Hop" to listOf(7.0f, 5.0f, 0f, 2.5f, 4.0f),
    "Jazz" to listOf(4.0f, 2.0f, -1.5f, 2.5f, 4.0f),
    "Pop" to listOf(-1.5f, 2.0f, 5.0f, 2.5f, -2.0f)
)

private val REVERB_PRESETS = listOf(
    "SMALL ROOM" to 1.toShort(),
    "MIDDLE ROOM" to 2.toShort(),
    "LARGE ROOM" to 3.toShort(),
    "MEDIUM HALL" to 4.toShort(),
    "LARGE HALL" to 5.toShort(),
    "PLATE" to 6.toShort()
)

@Composable
fun EqualizerScreen(
    modifier: Modifier = Modifier,
    initialCurve: EqualizerCurve = EqualizerCurve.FLAT,
    initialBassBoost: Int = 0,
    initialVirtualizer: Int = 0,
    initialLoudness: Int = 0,
    initialReverbPreset: Short = 0,
    customPresets: List<UserEqPresetDto> = emptyList(),
    onCurveChanged: (EqualizerCurve) -> Unit = {},
    onBassBoostChanged: (Int) -> Unit = {},
    onVirtualizerChanged: (Int) -> Unit = {},
    onLoudnessChanged: (Int) -> Unit = {},
    onReverbPresetChanged: (Short) -> Unit = {},
    onSaveCustomPreset: (name: String, curve: EqualizerCurve, bassBoost: Int, virtualizer: Int, loudness: Int) -> Unit = { _, _, _, _, _ -> },
    onAutoEqClick: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Equalizer, 1 = Volume
    var isEqEnabled by remember { mutableStateOf(true) }
    var selectedPresetName by remember { mutableStateOf("Custom") }
    var showPresetMenu by remember { mutableStateOf(false) }

    // 5 EQ bands (-15dB to +15dB)
    var bands by remember {
        val initial5 = if (initialCurve.bandsDb.size >= 5) {
            initialCurve.bandsDb.take(5)
        } else {
            List(5) { 0f }
        }
        mutableStateOf(initial5)
    }

    var bassBoost by remember { mutableIntStateOf(initialBassBoost) }
    var virtualizer by remember { mutableIntStateOf(initialVirtualizer) }
    var loudness by remember { mutableIntStateOf(initialLoudness) }
    var isAmplifierEnabled by remember { mutableStateOf(initialLoudness > 0) }
    var selectedReverb by remember { mutableStateOf(initialReverbPreset) }

    // Hardware stream volume
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var streamVol by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol.toFloat())
    }

    // Sound balance: 0.0 (full left) to 1.0 (full right), 0.5 center
    var soundBalance by remember { mutableFloatStateOf(0.5f) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    fun notifyEqChange(newBands: List<Float>) {
        bands = newBands
        // Map 5 bands to 10 bands for engine compatibility
        val tenBands = listOf(
            newBands[0], newBands[0],
            newBands[1], newBands[1],
            newBands[2], newBands[2],
            newBands[3], newBands[3],
            newBands[4], newBands[4]
        )
        onCurveChanged(EqualizerCurve(tenBands, 0f))
    }

    fun applyPreset(name: String, presetBands: List<Float>) {
        selectedPresetName = name
        notifyEqChange(presetBands)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1423),
                        Color(0xFF0A0D18),
                        UnboundBackground
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // ==================== Header & Sub-Tabs ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OnSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF00E5FF),
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 3.dp,
                            color = Color(0xFF00E5FF)
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Equalizer",
                                fontSize = 16.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) Color(0xFF00E5FF) else OnSurfaceVariant
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Volume",
                                fontSize = 16.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) Color(0xFF00E5FF) else OnSurfaceVariant
                            )
                        }
                    )
                }

                IconButton(
                    onClick = onAutoEqClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "AutoEq Headphone Presets",
                        tint = Color(0xFF00E5FF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ==================== Tab 0: Equalizer View ====================
            if (selectedTab == 0) {
                // EQ Top Controls: EQ Switch & Preset Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // EQ On/Off Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "EQ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Switch(
                            checked = isEqEnabled,
                            onCheckedChange = {
                                isEqEnabled = it
                                if (!it) {
                                    notifyEqChange(List(5) { 0f })
                                    onBassBoostChanged(0)
                                    onVirtualizerChanged(0)
                                } else {
                                    notifyEqChange(bands)
                                    onBassBoostChanged(bassBoost)
                                    onVirtualizerChanged(virtualizer)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00C6FF),
                                uncheckedThumbColor = OnSurfaceVariant,
                                uncheckedTrackColor = UnboundSurfaceContainerHigh
                            )
                        )
                    }

                    // Preset Dropdown
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1B2338))
                                .border(1.dp, Color(0xFF2A3654), RoundedCornerShape(20.dp))
                                .clickable { showPresetMenu = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = selectedPresetName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00E5FF)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Preset",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showPresetMenu,
                            onDismissRequest = { showPresetMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF1B2338))
                                .border(1.dp, Color(0xFF2A3654), RoundedCornerShape(8.dp))
                        ) {
                            BUILT_IN_EFFECT_PRESETS.forEach { (name, pBands) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            name,
                                            color = if (selectedPresetName == name) Color(0xFF00E5FF) else OnSurface,
                                            fontWeight = if (selectedPresetName == name) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        applyPreset(name, pBands)
                                        showPresetMenu = false
                                    }
                                )
                            }
                            if (customPresets.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFF2A3654))
                                customPresets.forEach { cp ->
                                    DropdownMenuItem(
                                        text = { Text(cp.name, color = OnSurface) },
                                        onClick = {
                                            selectedPresetName = cp.name
                                            val mapped = if (cp.bandGains.size >= 5) cp.bandGains.take(5) else List(5) { 0f }
                                            notifyEqChange(mapped)
                                            bassBoost = cp.bassBoost
                                            onBassBoostChanged(cp.bassBoost)
                                            virtualizer = cp.virtualizer
                                            onVirtualizerChanged(cp.virtualizer)
                                            showPresetMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Save Preset Icon
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B2338))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Custom Preset",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5-Band Vertical Equalizer Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF121727))
                        .border(1.dp, Color(0xFF1F2842), RoundedCornerShape(20.dp))
                        .padding(vertical = 20.dp, horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left dB scale indicator
                        Column(
                            modifier = Modifier.height(200.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("+15", fontSize = 10.sp, color = OnSurfaceVariant)
                            Text("0", fontSize = 10.sp, color = OnSurfaceVariant)
                            Text("-15", fontSize = 10.sp, color = OnSurfaceVariant)
                        }

                        // 5 Vertical Sliders
                        bands.forEachIndexed { index, gain ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = String.format("%+.1f", gain),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (gain != 0f) Color(0xFF00E5FF) else OnSurfaceVariant
                                )

                                // Custom vertical slider container
                                Box(
                                    modifier = Modifier
                                        .height(160.dp)
                                        .width(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Background vertical track
                                    Box(
                                        modifier = Modifier
                                            .height(150.dp)
                                            .width(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFF232B42))
                                    )

                                    // Interactive slider rotation
                                    androidx.compose.ui.viewinterop.AndroidView(
                                        modifier = Modifier
                                            .height(160.dp)
                                            .width(160.dp),
                                        factory = { ctx ->
                                            android.widget.SeekBar(ctx).apply {
                                                rotation = 270f
                                                max = 300 // -15.0 to +15.0 with step 0.1
                                                progress = ((gain + 15f) * 10f).toInt().coerceIn(0, 300)
                                                progressDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                                                thumb = android.graphics.drawable.GradientDrawable().apply {
                                                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                                                    setSize(40, 24)
                                                    cornerRadius = 6f
                                                    setColor(android.graphics.Color.parseColor("#00E5FF"))
                                                    setStroke(2, android.graphics.Color.WHITE)
                                                }
                                                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                                                    override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                                                        if (fromUser) {
                                                            val newDb = (p / 10f) - 15f
                                                            val updated = bands.toMutableList()
                                                            updated[index] = newDb
                                                            selectedPresetName = "Custom"
                                                            notifyEqChange(updated)
                                                        }
                                                    }
                                                    override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                                                    override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
                                                })
                                            }
                                        },
                                        update = { sb ->
                                            sb.progress = ((gain + 15f) * 10f).toInt().coerceIn(0, 300)
                                        }
                                    )
                                }

                                Text(
                                    text = FIVE_BAND_LABELS.getOrElse(index) { "" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OnSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Dual Rotary Knobs: Bass Booster & Virtualizer (Screenshot 5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RotaryKnob(
                        value = bassBoost / 1000f,
                        onValueChange = { frac ->
                            val newVal = (frac * 1000).toInt()
                            bassBoost = newVal
                            onBassBoostChanged(newVal)
                        },
                        label = "Bass",
                        valueText = "${bassBoost / 10}%",
                        size = 100.dp,
                        activeColor = Color(0xFF00C6FF),
                        indicatorColor = Color(0xFF00E5FF)
                    )

                    RotaryKnob(
                        value = virtualizer / 1000f,
                        onValueChange = { frac ->
                            val newVal = (frac * 1000).toInt()
                            virtualizer = newVal
                            onVirtualizerChanged(newVal)
                        },
                        label = "Virtualizer",
                        valueText = "${virtualizer / 10}%",
                        size = 100.dp,
                        activeColor = Color(0xFF00C6FF),
                        indicatorColor = Color(0xFF00E5FF)
                    )
                }
            }

            // ==================== Tab 1: Volume View ====================
            if (selectedTab == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 1. System Stream Volume
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF121727))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "VOLUME",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                            Text(
                                "${(streamVol * 100).toInt()}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00E5FF)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = streamVol,
                            onValueChange = {
                                streamVol = it
                                val targetVol = (it * maxVol).toInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00C6FF),
                                inactiveTrackColor = Color(0xFF232B42)
                            )
                        )
                    }

                    // 2. Amplifier (Loudness Gain)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF121727))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "AMPLIFIER",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                            Switch(
                                checked = isAmplifierEnabled,
                                onCheckedChange = {
                                    isAmplifierEnabled = it
                                    if (it) {
                                        if (loudness == 0) loudness = 500
                                        onLoudnessChanged(loudness)
                                    } else {
                                        onLoudnessChanged(0)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF00C6FF),
                                    uncheckedThumbColor = OnSurfaceVariant,
                                    uncheckedTrackColor = UnboundSurfaceContainerHigh
                                )
                            )
                        }

                        if (isAmplifierEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = loudness.toFloat(),
                                onValueChange = {
                                    loudness = it.toInt()
                                    onLoudnessChanged(loudness)
                                },
                                valueRange = 0f..1500f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00C6FF),
                                    inactiveTrackColor = Color(0xFF232B42)
                                )
                            )
                        }
                    }

                    // 3. Reverb Environment Selection (Screenshot 5)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF121727))
                            .padding(16.dp)
                    ) {
                        Text(
                            "REVERB",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2 rows of 3 chips
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                REVERB_PRESETS.take(3).forEach { (name, presetId) ->
                                    val isSelected = selectedReverb == presetId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF0077D4) else Color(0xFF1B2338))
                                            .border(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF2A3654), RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedReverb = if (isSelected) 0.toShort() else presetId
                                                onReverbPresetChanged(selectedReverb)
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else OnSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                REVERB_PRESETS.drop(3).forEach { (name, presetId) ->
                                    val isSelected = selectedReverb == presetId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF0077D4) else Color(0xFF1B2338))
                                            .border(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF2A3654), RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedReverb = if (isSelected) 0.toShort() else presetId
                                                onReverbPresetChanged(selectedReverb)
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else OnSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Sound Balance (Left / Right)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF121727))
                            .padding(16.dp)
                    ) {
                        Text(
                            "SOUND BALANCE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RotaryKnob(
                                value = (1f - soundBalance).coerceIn(0f, 1f),
                                onValueChange = { frac ->
                                    soundBalance = (1f - frac).coerceIn(0f, 1f)
                                },
                                label = "Left",
                                valueText = "${((1f - soundBalance) * 100).toInt()}%",
                                size = 80.dp,
                                activeColor = Color(0xFF00C6FF),
                                indicatorColor = Color(0xFF00E5FF)
                            )

                            RotaryKnob(
                                value = soundBalance.coerceIn(0f, 1f),
                                onValueChange = { frac ->
                                    soundBalance = frac.coerceIn(0f, 1f)
                                },
                                label = "Right",
                                valueText = "${(soundBalance * 100).toInt()}%",
                                size = 80.dp,
                                activeColor = Color(0xFF00C6FF),
                                indicatorColor = Color(0xFF00E5FF)
                            )
                        }
                    }
                }
            }
        }
    }

    // Save Custom Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Equalizer Preset", color = OnSurface) },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        focusedBorderColor = Color(0xFF00E5FF)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            val tenBands = listOf(
                                bands[0], bands[0],
                                bands[1], bands[1],
                                bands[2], bands[2],
                                bands[3], bands[3],
                                bands[4], bands[4]
                            )
                            onSaveCustomPreset(newPresetName.trim(), EqualizerCurve(tenBands, 0f), bassBoost, virtualizer, loudness)
                            selectedPresetName = newPresetName.trim()
                            showSaveDialog = false
                            newPresetName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C6FF))
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            },
            containerColor = Color(0xFF1B2338)
        )
    }
}
