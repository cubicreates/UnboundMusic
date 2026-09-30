/*
 * Package: autoeq
 * File: autoeq.go
 * Purpose: Parametric 10-band equalizer, Harman acoustic target curve calibration engine,
 *          and continuous logarithmic curve interpolation for headphone models.
 * Subsystem: Audio Processing & Sound Calibration
 * Concurrency: Thread-safe pure lookups and calculation functions safe for concurrent access.
 */

package autoeq

import (
	"fmt"
	"math"
	"strings"
	"sync"
)

// EQBand represents a single parametric biquad filter band.
type EQBand struct {
	FrequencyHz int     `json:"frequency_hz"`
	GainDB      float64 `json:"gain_db"`
	QFactor     float64 `json:"q_factor"`
}

// EQPreset contains 10-band equalization parameters and preamp gain for a specific headphone model.
type EQPreset struct {
	ModelID      string   `json:"model_id"`
	ModelName    string   `json:"model_name"`
	Brand        string   `json:"brand"`
	TargetCurve  string   `json:"target_curve"` // e.g. "Harman Over-Ear 2018", "Harman In-Ear 2019"
	PreampGainDB float64  `json:"preamp_gain_db"`
	Bands        []EQBand `json:"bands"`
}

// InterpolateGain calculates continuous logarithmic frequency response gain in dB.
func (p *EQPreset) InterpolateGain(targetFreqHz float64) float64 {
	if len(p.Bands) == 0 {
		return 0.0
	}
	if targetFreqHz <= float64(p.Bands[0].FrequencyHz) {
		return p.Bands[0].GainDB
	}
	lastIdx := len(p.Bands) - 1
	if targetFreqHz >= float64(p.Bands[lastIdx].FrequencyHz) {
		return p.Bands[lastIdx].GainDB
	}

	for i := 0; i < lastIdx; i++ {
		f0 := float64(p.Bands[i].FrequencyHz)
		f1 := float64(p.Bands[i+1].FrequencyHz)
		if targetFreqHz >= f0 && targetFreqHz <= f1 {
			// Logarithmic frequency interpolation
			logF0 := math.Log10(f0)
			logF1 := math.Log10(f1)
			logTarget := math.Log10(targetFreqHz)
			t := (logTarget - logF0) / (logF1 - logF0)
			return p.Bands[i].GainDB + t*(p.Bands[i+1].GainDB-p.Bands[i].GainDB)
		}
	}
	return 0.0
}

// ResampleCurve generates an EQBand list resampled to target arbitrary frequencies.
func (p *EQPreset) ResampleCurve(targetFreqs []int) []EQBand {
	resampled := make([]EQBand, len(targetFreqs))
	for i, f := range targetFreqs {
		resampled[i] = EQBand{
			FrequencyHz: f,
			GainDB:      math.Round(p.InterpolateGain(float64(f))*100) / 100,
			QFactor:     1.41,
		}
	}
	return resampled
}

// HeadphoneModel contains search metadata for a calibrated headphone profile.
type HeadphoneModel struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Brand     string `json:"brand"`
	Type      string `json:"type"` // "Over-Ear", "In-Ear / IEM", "TWS Earbuds"
	HasPreset bool   `json:"has_preset"`
}

// Engine coordinates headphone calibration curves and parametric EQ profiles.
type Engine struct {
	mu            sync.RWMutex
	presets       map[string]*EQPreset
	models        []HeadphoneModel
	resampleMu    sync.RWMutex
	resampleCache map[string][]EQBand
}

// StandardFrequencies defines the canonical 10-band octave frequencies (Hz).
var StandardFrequencies = []int{31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000}

// NewEngine initializes the AutoEq calibration engine with built-in reference headphone curves.
func NewEngine() *Engine {
	e := &Engine{
		presets:       make(map[string]*EQPreset),
		models:        make([]HeadphoneModel, 0, 50),
		resampleCache: make(map[string][]EQBand),
	}
	e.loadBuiltinPresets()
	return e
}

// SearchHeadphones finds calibrated headphone models matching a query string.
func (e *Engine) SearchHeadphones(query string) []HeadphoneModel {
	e.mu.RLock()
	defer e.mu.RUnlock()

	trimmed := strings.ToLower(strings.TrimSpace(query))
	if trimmed == "" {
		return e.models
	}

	var results []HeadphoneModel
	for _, m := range e.models {
		combined := strings.ToLower(fmt.Sprintf("%s %s", m.Brand, m.Name))
		if strings.Contains(combined, trimmed) {
			results = append(results, m)
		}
	}
	return results
}

// GetEQPreset retrieves the 10-band parametric EQ preset for a specific headphone model ID.
func (e *Engine) GetEQPreset(modelID string) (*EQPreset, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	preset, exists := e.presets[strings.ToLower(modelID)]
	if !exists {
		return nil, fmt.Errorf("headphone preset not found for ID: %s", modelID)
	}
	return preset, nil
}

// GetResampledPreset retrieves pre-computed or memoized resampled parametric EQ curves for a headphone model.
func (e *Engine) GetResampledPreset(modelID string, targetFreqs []int) (*EQPreset, error) {
	preset, err := e.GetEQPreset(modelID)
	if err != nil {
		return nil, err
	}

	if len(targetFreqs) == 0 {
		return preset, nil
	}

	cacheKey := fmt.Sprintf("%s:%v", strings.ToLower(modelID), targetFreqs)
	e.resampleMu.RLock()
	cachedBands, found := e.resampleCache[cacheKey]
	e.resampleMu.RUnlock()

	if found {
		resampled := *preset
		resampled.Bands = cachedBands
		return &resampled, nil
	}

	// Compute and store in cache
	bands := preset.ResampleCurve(targetFreqs)

	e.resampleMu.Lock()
	e.resampleCache[cacheKey] = bands
	e.resampleMu.Unlock()

	resampled := *preset
	resampled.Bands = bands
	return &resampled, nil
}

// loadBuiltinPresets registers industry-standard calibrated profiles.
func (e *Engine) loadBuiltinPresets() {
	// 1. Sony WH-1000XM5
	e.registerPreset(&EQPreset{
		ModelID:      "sony_wh1000xm5",
		ModelName:    "WH-1000XM5",
		Brand:        "Sony",
		TargetCurve:  "Harman Over-Ear 2018",
		PreampGainDB: -5.4,
		Bands: []EQBand{
			{FrequencyHz: 31, GainDB: -1.2, QFactor: 1.41},
			{FrequencyHz: 62, GainDB: -3.8, QFactor: 1.41},
			{FrequencyHz: 125, GainDB: -4.5, QFactor: 1.41},
			{FrequencyHz: 250, GainDB: -1.0, QFactor: 1.41},
			{FrequencyHz: 500, GainDB: 0.5, QFactor: 1.41},
			{FrequencyHz: 1000, GainDB: 1.8, QFactor: 1.41},
			{FrequencyHz: 2000, GainDB: 3.2, QFactor: 1.41},
			{FrequencyHz: 4000, GainDB: -2.1, QFactor: 1.41},
			{FrequencyHz: 8000, GainDB: 2.0, QFactor: 1.41},
			{FrequencyHz: 16000, GainDB: -0.5, QFactor: 1.41},
		},
	}, "Over-Ear")

	// 2. Apple AirPods Pro 2
	e.registerPreset(&EQPreset{
		ModelID:      "apple_airpods_pro_2",
		ModelName:    "AirPods Pro 2",
		Brand:        "Apple",
		TargetCurve:  "Harman In-Ear 2019",
		PreampGainDB: -3.1,
		Bands: []EQBand{
			{FrequencyHz: 31, GainDB: 0.5, QFactor: 1.41},
			{FrequencyHz: 62, GainDB: -1.2, QFactor: 1.41},
			{FrequencyHz: 125, GainDB: -2.0, QFactor: 1.41},
			{FrequencyHz: 250, GainDB: -0.5, QFactor: 1.41},
			{FrequencyHz: 500, GainDB: 0.0, QFactor: 1.41},
			{FrequencyHz: 1000, GainDB: 0.8, QFactor: 1.41},
			{FrequencyHz: 2000, GainDB: 1.5, QFactor: 1.41},
			{FrequencyHz: 4000, GainDB: -1.0, QFactor: 1.41},
			{FrequencyHz: 8000, GainDB: 1.2, QFactor: 1.41},
			{FrequencyHz: 16000, GainDB: 0.0, QFactor: 1.41},
		},
	}, "TWS Earbuds")

	// 3. Sennheiser HD 650
	e.registerPreset(&EQPreset{
		ModelID:      "sennheiser_hd650",
		ModelName:    "HD 650",
		Brand:        "Sennheiser",
		TargetCurve:  "Harman Over-Ear 2018",
		PreampGainDB: -6.5,
		Bands: []EQBand{
			{FrequencyHz: 31, GainDB: 5.5, QFactor: 1.41},
			{FrequencyHz: 62, GainDB: 4.2, QFactor: 1.41},
			{FrequencyHz: 125, GainDB: 1.0, QFactor: 1.41},
			{FrequencyHz: 250, GainDB: -0.5, QFactor: 1.41},
			{FrequencyHz: 500, GainDB: 0.0, QFactor: 1.41},
			{FrequencyHz: 1000, GainDB: -0.8, QFactor: 1.41},
			{FrequencyHz: 2000, GainDB: 1.0, QFactor: 1.41},
			{FrequencyHz: 4000, GainDB: 2.5, QFactor: 1.41},
			{FrequencyHz: 8000, GainDB: -1.0, QFactor: 1.41},
			{FrequencyHz: 16000, GainDB: 1.5, QFactor: 1.41},
		},
	}, "Over-Ear")

	// 4. Audio-Technica ATH-M50x
	e.registerPreset(&EQPreset{
		ModelID:      "audio_technica_ath_m50x",
		ModelName:    "ATH-M50x",
		Brand:        "Audio-Technica",
		TargetCurve:  "Harman Over-Ear 2018",
		PreampGainDB: -4.8,
		Bands: []EQBand{
			{FrequencyHz: 31, GainDB: -1.0, QFactor: 1.41},
			{FrequencyHz: 62, GainDB: -2.5, QFactor: 1.41},
			{FrequencyHz: 125, GainDB: -3.2, QFactor: 1.41},
			{FrequencyHz: 250, GainDB: -0.8, QFactor: 1.41},
			{FrequencyHz: 500, GainDB: 1.0, QFactor: 1.41},
			{FrequencyHz: 1000, GainDB: 1.5, QFactor: 1.41},
			{FrequencyHz: 2000, GainDB: -1.8, QFactor: 1.41},
			{FrequencyHz: 4000, GainDB: 2.0, QFactor: 1.41},
			{FrequencyHz: 8000, GainDB: -3.5, QFactor: 1.41},
			{FrequencyHz: 16000, GainDB: 0.5, QFactor: 1.41},
		},
	}, "Over-Ear")

	// 5. Moondrop Blessing 2
	e.registerPreset(&EQPreset{
		ModelID:      "moondrop_blessing2",
		ModelName:    "Blessing 2",
		Brand:        "Moondrop",
		TargetCurve:  "Harman In-Ear 2019",
		PreampGainDB: -4.2,
		Bands: []EQBand{
			{FrequencyHz: 31, GainDB: 2.8, QFactor: 1.41},
			{FrequencyHz: 62, GainDB: 2.1, QFactor: 1.41},
			{FrequencyHz: 125, GainDB: 0.5, QFactor: 1.41},
			{FrequencyHz: 250, GainDB: -0.2, QFactor: 1.41},
			{FrequencyHz: 500, GainDB: 0.0, QFactor: 1.41},
			{FrequencyHz: 1000, GainDB: 0.5, QFactor: 1.41},
			{FrequencyHz: 2000, GainDB: -0.8, QFactor: 1.41},
			{FrequencyHz: 4000, GainDB: 1.2, QFactor: 1.41},
			{FrequencyHz: 8000, GainDB: -1.5, QFactor: 1.41},
			{FrequencyHz: 16000, GainDB: 0.8, QFactor: 1.41},
		},
	}, "In-Ear / IEM")

	// 6. Beyerdynamic DT 770 Pro 80 Ohm
	e.registerPreset(&EQPreset{
		ModelID:      "beyerdynamic_dt770pro_80",
		ModelName:    "DT 770 Pro (80 Ohm)",
		Brand:        "Beyerdynamic",
		TargetCurve:  "Harman Over-Ear 2018",
		PreampGainDB: -5.8,
		Bands: []EQBand{
			{FrequencyHz: 31, GainDB: -2.0, QFactor: 1.41},
			{FrequencyHz: 62, GainDB: -3.5, QFactor: 1.41},
			{FrequencyHz: 125, GainDB: -1.5, QFactor: 1.41},
			{FrequencyHz: 250, GainDB: 0.0, QFactor: 1.41},
			{FrequencyHz: 500, GainDB: 1.2, QFactor: 1.41},
			{FrequencyHz: 1000, GainDB: 1.0, QFactor: 1.41},
			{FrequencyHz: 2000, GainDB: 2.0, QFactor: 1.41},
			{FrequencyHz: 4000, GainDB: -1.5, QFactor: 1.41},
			{FrequencyHz: 8000, GainDB: -4.5, QFactor: 1.41},
			{FrequencyHz: 16000, GainDB: -1.0, QFactor: 1.41},
		},
	}, "Over-Ear")
}

func (e *Engine) registerPreset(preset *EQPreset, hpType string) {
	e.presets[preset.ModelID] = preset
	e.models = append(e.models, HeadphoneModel{
		ID:        preset.ModelID,
		Name:      preset.ModelName,
		Brand:     preset.Brand,
		Type:      hpType,
		HasPreset: true,
	})
}
