/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: EqualizerViewModel.kt
 * Purpose: Dedicated Domain ViewModel for 10-band IIR DSP equalization, AutoEq headphone target
 *          compensation database search/apply, Bass Boost, Virtualizer, and Loudness Enhancer.
 * Subsystem: Domain Layer / Audio DSP
 * Concurrency: Thread-safe reactive StateFlow orchestration on viewModelScope.
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.UserEqPresetDto
import com.cubicreates.unboundmusic.service.ServiceConnection
import com.cubicreates.unboundmusic.ui.equalizer.AutoEqHeadphoneItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Domain ViewModel responsible for 10-band graphic/parametric DSP and AutoEq profile management.
 */
class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val daemonManager = DaemonManager.getInstance(application)
    private val client = daemonManager.client
    private val serviceConnection = ServiceConnection.getInstance(application)

    private val _equalizerCurve = MutableStateFlow(EqualizerCurve.FLAT)
    val equalizerCurve: StateFlow<EqualizerCurve> = _equalizerCurve.asStateFlow()

    private val _autoEqResults = MutableStateFlow<List<AutoEqHeadphoneItem>>(emptyList())
    val autoEqResults: StateFlow<List<AutoEqHeadphoneItem>> = _autoEqResults.asStateFlow()

    private val _isSearchingAutoEq = MutableStateFlow(false)
    val isSearchingAutoEq: StateFlow<Boolean> = _isSearchingAutoEq.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _loudnessGainMb = MutableStateFlow(0)
    val loudnessGainMb: StateFlow<Int> = _loudnessGainMb.asStateFlow()

    private val _customPresets = MutableStateFlow<List<UserEqPresetDto>>(emptyList())
    val customPresets: StateFlow<List<UserEqPresetDto>> = _customPresets.asStateFlow()

    companion object {
        private const val TAG = "EqualizerViewModel"
    }

    fun setEqualizerCurve(curve: EqualizerCurve) {
        _equalizerCurve.value = curve
        serviceConnection.setEqualizerCurve(curve)
    }

    fun searchAutoEqPresets(query: String) {
        if (query.isBlank()) {
            _autoEqResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingAutoEq.value = true
            try {
                val (code, resp) = client.autoEqSearch(query)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val results = json.optJSONArray("results")
                    val items = mutableListOf<AutoEqHeadphoneItem>()
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val r = results.getJSONObject(i)
                            items.add(
                                AutoEqHeadphoneItem(
                                    id = r.optString("id", r.optString("name")),
                                    name = r.optString("name", "Unknown Model"),
                                    source = r.optString("source", "Harman Target")
                                )
                            )
                        }
                    }
                    _autoEqResults.value = items
                }
            } catch (e: Exception) {
                Log.d(TAG, "AutoEq search error: ${e.message}")
            } finally {
                _isSearchingAutoEq.value = false
            }
        }
    }

    fun applyAutoEqPreset(item: AutoEqHeadphoneItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.autoEqPreset(item.id)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val preamp = json.optDouble("preamp_db", 0.0).toFloat()
                    val bandsArr = json.optJSONArray("bands_db")
                    val bandsList = mutableListOf<Float>()
                    if (bandsArr != null) {
                        for (i in 0 until bandsArr.length()) {
                            bandsList.add(bandsArr.getDouble(i).toFloat())
                        }
                    }
                    if (bandsList.size == 10) {
                        val curve = EqualizerCurve(bandsList, preamp)
                        setEqualizerCurve(curve)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Apply AutoEq error: ${e.message}")
            }
        }
    }

    fun setBassBoost(strength: Int) {
        _bassBoostStrength.value = strength
        serviceConnection.setBassBoost(strength)
        viewModelScope.launch(Dispatchers.IO) {
            client.setAppSetting("eq_bass_boost", strength.toString())
        }
    }

    fun setVirtualizer(strength: Int) {
        _virtualizerStrength.value = strength
        serviceConnection.setVirtualizer(strength)
        viewModelScope.launch(Dispatchers.IO) {
            client.setAppSetting("eq_virtualizer", strength.toString())
        }
    }

    fun setLoudness(gainMb: Int) {
        _loudnessGainMb.value = gainMb
        serviceConnection.setLoudness(gainMb)
        viewModelScope.launch(Dispatchers.IO) {
            client.setAppSetting("eq_loudness", gainMb.toString())
        }
    }

    fun clearAutoEqResults() {
        _autoEqResults.value = emptyList()
    }
}
