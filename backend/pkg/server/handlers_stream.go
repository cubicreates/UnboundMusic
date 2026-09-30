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
