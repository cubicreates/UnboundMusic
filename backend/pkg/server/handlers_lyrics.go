package server

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/lyrics"
)

// handleLyrics handles multi-tier synchronized lyrics resolution with on-device forced alignment and SQLite caching.
func (s *Server) handleLyrics(w http.ResponseWriter, r *http.Request) {
	trackID := r.URL.Query().Get("id")
	title := r.URL.Query().Get("title")
	artist := r.URL.Query().Get("artist")
	durationStr := r.URL.Query().Get("duration")

	var durationMs int64 = 180000
	if durationStr != "" {
		if val, err := strconv.ParseInt(durationStr, 10, 64); err == nil && val > 0 {
			durationMs = val
		}
	}

	if trackID == "" && title == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' or 'title' is required")
		return
	}

	// Resolve metadata from local repository if title or artist is missing or dummy
	if trackID != "" && (title == "" || artist == "" || strings.EqualFold(artist, "Song") || strings.EqualFold(artist, "YouTube Artist") || strings.EqualFold(artist, "Video")) {
		if trk, err := s.repo.GetTrack(r.Context(), trackID); err == nil && trk != nil {
			if title == "" {
				title = trk.Title
			}
			if artist == "" || strings.EqualFold(artist, "Song") || strings.EqualFold(artist, "YouTube Artist") || strings.EqualFold(artist, "Video") {
				if trk.Artist != "" && !strings.EqualFold(trk.Artist, "Song") && !strings.EqualFold(trk.Artist, "YouTube Artist") && !strings.EqualFold(trk.Artist, "Video") {
					artist = trk.Artist
				}
			}
			if durationMs == 180000 && trk.DurationMs > 0 {
				durationMs = trk.DurationMs
			}
		}
	}

	cleanTitle := lyrics.CleanTrackTitle(title)
	if cleanTitle == "" {
		cleanTitle = strings.TrimSpace(title)
	}

	cleanArtist := strings.TrimSpace(artist)
	switch strings.ToLower(cleanArtist) {
	case "song", "video", "youtube artist", "artist":
		cleanArtist = ""
	}

	// Tier 0: SQLite Local Cache (0ms latency)
	if trackID != "" {
		cached, err := s.repo.GetLyrics(r.Context(), trackID)
		if err == nil && cached != nil && len(cached.Lines) > 0 {
			writeJSON(w, http.StatusOK, cached)
			return
		}
	}

	durationSec := int(durationMs / 1000)

	// Tier 1: Query LRCLIB for verified, true millisecond-synced lyrics with search fallback
	lrclibPayload, err := s.geniusClient.FetchLRCLIBSynced(r.Context(), cleanTitle, cleanArtist, durationSec)
	if err == nil && lrclibPayload != nil && len(lrclibPayload.Lines) > 0 {
		lrclibPayload.TrackID = trackID
		lrclibPayload.Source = "LRCLIB (Verified Synced)"
		_ = s.repo.SaveLyrics(r.Context(), lrclibPayload)
		writeJSON(w, http.StatusOK, lrclibPayload)
		return
	}

	// Tier 2: Query NetEase Cloud Music synced LRC
	if s.neteaseClient != nil && cleanTitle != "" {
		neteasePayload, err := s.neteaseClient.Fetch(r.Context(), cleanTitle, cleanArtist, durationSec)
		if err == nil && neteasePayload != nil && len(neteasePayload.Lines) > 0 {
			neteasePayload.TrackID = trackID
			_ = s.repo.SaveLyrics(r.Context(), neteasePayload)
			writeJSON(w, http.StatusOK, neteasePayload)
			return
		}
	}

	// Tier 3: Query YouTube InnerTube timed captions
	if s.ytClient != nil && trackID != "" {
		ytPayload, err := lyrics.FetchYouTubeCaptions(r.Context(), s.ytClient, trackID, cleanTitle, cleanArtist)
		if err == nil && ytPayload != nil && len(ytPayload.Lines) > 0 {
			_ = s.repo.SaveLyrics(r.Context(), ytPayload)
			writeJSON(w, http.StatusOK, ytPayload)
			return
		}
	}

	// Tier 4: Query Genius for complete uncensored lyrics and apply on-device phonetic alignment
	hit, err := s.geniusClient.SearchSong(r.Context(), cleanTitle, cleanArtist)
	if err == nil && hit != nil {
		plainPayload, err := s.geniusClient.FetchLyrics(r.Context(), hit)
		if err == nil && len(plainPayload.Lines) > 0 {
			aligned, err := s.aligner.AlignLyrics(trackID, hit.Title, hit.Artist, plainPayload.PlainLyrics, durationMs)
			if err == nil {
				_ = s.repo.SaveLyrics(r.Context(), aligned)
				writeJSON(w, http.StatusOK, aligned)
				return
			}
		}
	}

	writeError(w, http.StatusNotFound, "lyrics not found")
}
