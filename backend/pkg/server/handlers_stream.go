package server

import (
	"fmt"
	"net/http"
	"net/url"

	"github.com/cubicreates/unbound-engine/pkg/router"
)

// handleStream handles zero-data hybrid stream resolution.
func (s *Server) handleStream(w http.ResponseWriter, r *http.Request) {
	trackID := r.URL.Query().Get("id")
	title := r.URL.Query().Get("title")
	artist := r.URL.Query().Get("artist")

	if trackID == "" && title == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' or 'title' is required")
		return
	}

	stream, err := s.router.ResolvePlayback(r.Context(), trackID, title, artist)
	if err != nil && s.fallbackCoord != nil && (title != "" || trackID != "") {
		// Upstream Health Canary / Silent Fallback:
		// When YouTube extraction fails (e.g. cipher changes, regional block, or 403),
		// attempt silent resolution via the multi-stage fallback engine instead of failing.
		queryTitle := title
		if queryTitle == "" {
			queryTitle = trackID
		}
		fallbackStatus, fbErr := s.fallbackCoord.ResolveAndStream(r.Context(), queryTitle, artist)
		if fbErr == nil && fallbackStatus != nil && fallbackStatus.SelectedSource != nil {
			resolvedTitle := queryTitle
			if fallbackStatus.VerifiedTrack != nil && fallbackStatus.VerifiedTrack.Title != "" {
				resolvedTitle = fallbackStatus.VerifiedTrack.Title
			}
			resolvedArtist := artist
			if fallbackStatus.VerifiedTrack != nil && fallbackStatus.VerifiedTrack.Artist != "" {
				resolvedArtist = fallbackStatus.VerifiedTrack.Artist
			}
			streamURL := fmt.Sprintf("http://127.0.0.1:%d/api/v1/proxy/stream?id=%s", s.cfg.Port, fallbackStatus.SelectedSource.InfoHash)
			stream = &router.ResolvedStream{
				TrackID:         fallbackStatus.SelectedSource.InfoHash,
				Title:           resolvedTitle,
				Artist:          resolvedArtist,
				StreamURL:       streamURL,
				DirectStreamURL: streamURL,
				StreamType:      "p2p_fallback",
				Codec:           fallbackStatus.SelectedSource.AudioFormat,
				BitrateKbps:     320,
				DataConsumed:    0,
			}
			err = nil
		}
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	// For remote audio streams, provide both the direct YouTube CDN signed URL and the local reverse proxy URL.
	if stream.StreamType != router.StreamTypeLocalZeroData && stream.TrackID != "" {
		quality := r.URL.Query().Get("quality")
		qualityParam := ""
		if quality != "" {
			qualityParam = "&quality=" + url.QueryEscape(quality)
		}
		stream.ProxyStreamURL = fmt.Sprintf("http://127.0.0.1:%d/api/v1/proxy/stream?id=%s%s", s.cfg.Port, stream.TrackID, qualityParam)
		if stream.DirectStreamURL == "" {
			stream.DirectStreamURL = stream.StreamURL
		}
		// Default to proxy stream for robust lookahead ring buffering and dropout-free playback;
		// allows ?direct=true to bypass the proxy if needed.
		if r.URL.Query().Get("direct") != "true" {
			stream.StreamURL = stream.ProxyStreamURL
		}
	}

	writeJSON(w, http.StatusOK, stream)
}
