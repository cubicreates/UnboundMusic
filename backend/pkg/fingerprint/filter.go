/*
 * Package: fingerprint
 * File: filter.go
 * Purpose: Classifies audio files into music tracks vs. noise, sound effects, ringtones, and voice notes.
 * Subsystem: Storage & Fingerprint Engine
 * Concurrency: Pure stateless functions safe for concurrent execution across worker goroutines.
 */

package fingerprint

import (
	"path/filepath"
	"regexp"
	"strings"
)

const (
	// MinMusicDurationMs is the minimum threshold (30 seconds) for music classification.
	MinMusicDurationMs = 30 * 1000
)

var (
	// regexVoiceMemo patterns common to messaging apps and default voice recorders
	regexVoiceMemo = regexp.MustCompile(`(?i)^(aud|ptt|voice|recording|memo|call|rec|msg|whatsapp_audio|telegram_audio)[\-_0-9]`)

	// regexNoiseKeywords identifies sound effects, notification tones, and ringtones
	regexNoiseKeywords = regexp.MustCompile(`(?i)(notification|ringtone|sound_effect|sfx|beep|alarm|alert|voicemail|hangout|system_sound)`)
)

// ClassificationResult encapsulates the analysis outcome for an audio file.
type ClassificationResult struct {
	IsMusic bool   `json:"is_music"`
	Reason  string `json:"reason"`
}

// ClassifyAudio determines whether an audio file is a genuine music track or disposable voice memo/noise.
func ClassifyAudio(meta *AudioMetadata) ClassificationResult {
	if meta == nil {
		return ClassificationResult{IsMusic: false, Reason: "null audio metadata"}
	}

	// 1. Duration Constraint: Must be at least 30 seconds
	if meta.DurationMs > 0 && meta.DurationMs < MinMusicDurationMs {
		return ClassificationResult{
			IsMusic: false,
			Reason:  "duration under 30 seconds (likely voice note or sound effect)",
		}
	}

	fileName := filepath.Base(meta.FilePath)
	nameWithoutExt := strings.TrimSuffix(fileName, filepath.Ext(fileName))

	// 2. Keyword & Pattern Matching for Voice Memos
	if regexVoiceMemo.MatchString(nameWithoutExt) {
		return ClassificationResult{
			IsMusic: false,
			Reason:  "filename matches voice recording pattern (e.g. AUD-, PTT-, Voice_)",
		}
	}

	// 3. Keyword Matching for Notification Sounds and SFX
	if regexNoiseKeywords.MatchString(nameWithoutExt) {
		return ClassificationResult{
			IsMusic: false,
			Reason:  "filename contains noise/ringtone/notification keywords",
		}
	}

	// 4. Sample Rate Constraint: Low fidelity voice recording (< 22kHz mono)
	if meta.SampleRate > 0 && meta.SampleRate < 22050 && meta.Channels == 1 {
		return ClassificationResult{
			IsMusic: false,
			Reason:  "narrowband mono voice frequency profile (< 22.05kHz)",
		}
	}

	return ClassificationResult{
		IsMusic: true,
		Reason:  "valid music audio profile",
	}
}
