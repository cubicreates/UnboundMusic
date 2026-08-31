/*
 * Package: updater
 * File: updater.go
 * Purpose: In-app GitHub release auto-updater: detects new version releases, parses markdown changelogs, and provides direct binary/APK asset download links.
 * Subsystem: Application Lifecycle & Updates
 * Concurrency: Thread-safe HTTP queries with timeout guards.
 */

package updater

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

// UpdateInfo holds version details and download URLs.
type UpdateInfo struct {
	CurrentVersion  string `json:"current_version"`
	LatestVersion   string `json:"latest_version"`
	HasUpdate       bool   `json:"has_update"`
	ReleaseNotes    string `json:"release_notes"`
	DownloadURL     string `json:"download_url"`
	PublishedAt     string `json:"published_at"`
}

// Updater coordinates GitHub release checks.
type Updater struct {
	currentVersion string
	repoOwner      string
	repoName       string
	httpClient     *http.Client
}

// NewUpdater initializes the GitHub release checker.
func NewUpdater(currentVersion string) *Updater {
	if currentVersion == "" {
		currentVersion = "1.0.0"
	}
	return &Updater{
		currentVersion: currentVersion,
		repoOwner:      "cubicreates",
		repoName:       "UnboundMusic",
		httpClient:     &http.Client{Timeout: 8 * time.Second},
	}
}

// CheckForUpdates queries GitHub Releases API for the latest tag.
func (u *Updater) CheckForUpdates(ctx context.Context) (*UpdateInfo, error) {
	apiURL := fmt.Sprintf("https://api.github.com/repos/%s/%s/releases/latest", u.repoOwner, u.repoName)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, apiURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "UnboundMusic-Desktop/1.0.0")

	resp, err := u.httpClient.Do(req)
	if err != nil {
		// Return offline update status without error
		return &UpdateInfo{
			CurrentVersion: u.currentVersion,
			LatestVersion:  u.currentVersion,
			HasUpdate:      false,
			ReleaseNotes:   "Offline or unable to connect to GitHub.",
		}, nil
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return &UpdateInfo{
			CurrentVersion: u.currentVersion,
			LatestVersion:  u.currentVersion,
			HasUpdate:      false,
			ReleaseNotes:   "Up to date.",
		}, nil
	}

	var ghRelease struct {
		TagName string `json:"tag_name"`
		Body    string `json:"body"`
		HTMLURL string `json:"html_url"`
		Assets  []struct {
			BrowserDownloadURL string `json:"browser_download_url"`
		} `json:"assets"`
	}

	_ = json.NewDecoder(resp.Body).Decode(&ghRelease)

	hasUpdate := ghRelease.TagName != "" && ghRelease.TagName != "v"+u.currentVersion && ghRelease.TagName != u.currentVersion
	downloadURL := ghRelease.HTMLURL
	if len(ghRelease.Assets) > 0 {
		downloadURL = ghRelease.Assets[0].BrowserDownloadURL
	}

	return &UpdateInfo{
		CurrentVersion: u.currentVersion,
		LatestVersion:  ghRelease.TagName,
		HasUpdate:      hasUpdate,
		ReleaseNotes:   ghRelease.Body,
		DownloadURL:    downloadURL,
		PublishedAt:    time.Now().Format(time.RFC3339),
	}, nil
}
