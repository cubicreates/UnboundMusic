/*
 * Package: main
 * File: handlers_podcasts.go
 * Purpose: Standalone daemon HTTP handlers for dedicated Podcast discovery, search, browse, and resume tracking.
 */

package main

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/podcasts"
)

func (d *Daemon) HandlePodcastDiscover(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	category := strings.TrimSpace(r.URL.Query().Get("category"))
	country := strings.TrimSpace(r.URL.Query().Get("gl"))
	feed, err := d.podcastEng.DiscoverPodcasts(r.Context(), category, country)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	d.writeJSON(w, http.StatusOK, feed)
}

func (d *Daemon) HandlePodcastSearch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	query := strings.TrimSpace(r.URL.Query().Get("q"))
	if query == "" {
		d.writeError(w, http.StatusBadRequest, "query parameter 'q' is required")
		return
	}
	filter := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("filter")))
	if filter == "" {
		filter = "all"
	}
	episodes, shows, err := d.podcastEng.SearchPodcastsWithFilter(r.Context(), query, filter)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	d.writeJSON(w, http.StatusOK, map[string]any{
		"query":    query,
		"domain":   "podcasts",
		"filter":   filter,
		"episodes": episodes,
		"shows":    shows,
		"count":    len(episodes),
	})
}

func (d *Daemon) HandlePodcastBrowse(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	id := strings.TrimSpace(r.URL.Query().Get("id"))
	title := strings.TrimSpace(r.URL.Query().Get("title"))
	if id == "" && title == "" {
		d.writeError(w, http.StatusBadRequest, "parameter 'id' or 'title' is required")
		return
	}
	show, err := d.podcastEng.BrowseShowWithTitle(r.Context(), id, title)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	d.writeJSON(w, http.StatusOK, show)
}

func (d *Daemon) HandlePodcastResume(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		epID := strings.TrimSpace(r.URL.Query().Get("episode_id"))
		if epID == "" {
			d.writeError(w, http.StatusBadRequest, "episode_id is required")
			return
		}
		d.writeJSON(w, http.StatusOK, map[string]any{
			"episode_id":  epID,
			"position_ms": d.podcastEng.GetResumePosition(epID),
		})
	case http.MethodPost:
		type ResumeReq struct {
			EpisodeID    string `json:"episode_id"`
			PositionMs   int64  `json:"position_ms"`
			Title        string `json:"title,omitempty"`
			ShowTitle    string `json:"show_title,omitempty"`
			Author       string `json:"author,omitempty"`
			ThumbnailURL string `json:"thumbnail_url,omitempty"`
			DurationMs   int64  `json:"duration_ms,omitempty"`
		}
		var req ResumeReq
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.EpisodeID) == "" {
			d.writeError(w, http.StatusBadRequest, "valid 'episode_id' is required")
			return
		}
		d.podcastEng.SaveResumePosition(req.EpisodeID, req.PositionMs)
		if strings.TrimSpace(req.Title) != "" {
			d.podcastEng.RecordEpisodeMetadata(r.Context(), podcasts.PodcastEpisode{
				ID:           req.EpisodeID,
				ShowID:       req.ShowTitle,
				ShowTitle:    req.ShowTitle,
				Author:       req.Author,
				Title:        req.Title,
				DurationMs:   req.DurationMs,
				ThumbnailURL: req.ThumbnailURL,
				ResumePosMs:  req.PositionMs,
			})
		}
		d.writeJSON(w, http.StatusOK, map[string]any{
			"status":      "SAVED",
			"episode_id":  req.EpisodeID,
			"position_ms": req.PositionMs,
		})
	default:
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (d *Daemon) HandlePodcastLibrary(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	state := d.podcastEng.GetLibraryState(r.Context())
	d.writeJSON(w, http.StatusOK, state)
}

func (d *Daemon) HandlePodcastFollow(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	var show podcasts.PodcastShow
	if err := json.NewDecoder(r.Body).Decode(&show); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid show payload")
		return
	}
	followed, err := d.podcastEng.ToggleFollowShow(r.Context(), show)
	if err != nil {
		d.writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	d.writeJSON(w, http.StatusOK, map[string]any{
		"followed": followed,
		"show_id":  show.ID,
	})
}

func (d *Daemon) HandlePodcastSaveEpisode(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	var ep podcasts.PodcastEpisode
	if err := json.NewDecoder(r.Body).Decode(&ep); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid episode payload")
		return
	}
	saved, err := d.podcastEng.ToggleSaveEpisode(r.Context(), ep)
	if err != nil {
		d.writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	d.writeJSON(w, http.StatusOK, map[string]any{
		"saved":      saved,
		"episode_id": ep.ID,
	})
}
