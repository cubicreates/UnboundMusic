/*
 * Package: updater
 * File: updater.go
 * Purpose: In-app auto-updater: queries the Unbound Music marketing website backend
 *          (https://unboundmusic-two.vercel.app/api/version) and compares remote vs installed app version.
 * Subsystem: Application Lifecycle & Updates
 * Concurrency: Thread-safe HTTP queries with timeout guards.
 */

package updater

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const (
	DefaultVersionAPIURL = "https://unboundmusic-two.vercel.app/api/version"
	DefaultDownloadURL   = "https://unboundmusic-two.vercel.app/download"
)

// UpdateInfo holds version details and download URLs.
type UpdateInfo struct {
	CurrentVersion string `json:"current_version"`
	LatestVersion  string `json:"latest_version"`
	HasUpdate      bool   `json:"has_update"`
	ReleaseNotes   string `json:"release_notes"`
	DownloadURL    string `json:"download_url"`
	PublishedAt    string `json:"published_at"`
}

// Updater coordinates marketing website version checks.
type Updater struct {
	currentVersion string
	versionAPIURL  string
	downloadURL    string
	httpClient     *http.Client
}

// NewUpdater initializes the version checker against the marketing website backend.
func NewUpdater(currentVersion string) *Updater {
	if currentVersion == "" {
		currentVersion = "1.0.27.11.26"
	}
	return &Updater{
		currentVersion: currentVersion,
		versionAPIURL:  DefaultVersionAPIURL,
		downloadURL:    DefaultDownloadURL,
		httpClient:     &http.Client{Timeout: 8 * time.Second},
	}
}

// IsNewerVersion returns true strictly when remote is newer than current.
// Supports both standard semver (M.m.p) and 5-part date versions (M.m.DD.MM.YY).
func IsNewerVersion(remote, current string) bool {
	rClean := strings.TrimSpace(strings.TrimPrefix(strings.TrimPrefix(remote, "v"), "V"))
	cClean := strings.TrimSpace(strings.TrimPrefix(strings.TrimPrefix(current, "v"), "V"))
	if rClean == "" || cClean == "" || rClean == cClean {
		return false
	}

	rParts := parseVersionParts(rClean)
	cParts := parseVersionParts(cClean)

	// Handle 5-part date-encoded versions: major.minor.DD.MM.YY -> compare major, minor, YY, MM, DD
	if len(rParts) == 5 && len(cParts) == 5 {
		order := []int{0, 1, 4, 3, 2}
		for _, idx := range order {
			if rParts[idx] != cParts[idx] {
				return rParts[idx] > cParts[idx]
			}
		}
		return false
	}

	maxLen := len(rParts)
	if len(cParts) > maxLen {
		maxLen = len(cParts)
	}
	for i := 0; i < maxLen; i++ {
		rv := 0
		if i < len(rParts) {
			rv = rParts[i]
		}
		cv := 0
		if i < len(cParts) {
			cv = cParts[i]
		}
		if rv != cv {
			return rv > cv
		}
	}
	return false
}

func parseVersionParts(v string) []int {
	raw := strings.Split(v, ".")
	parts := make([]int, 0, len(raw))
	for _, seg := range raw {
		n, err := strconv.Atoi(strings.TrimSpace(seg))
		if err != nil {
			n = 0
		}
		parts = append(parts, n)
	}
	return parts
}

// CheckForUpdates queries the marketing website backend using the default version.
func (u *Updater) CheckForUpdates(ctx context.Context) (*UpdateInfo, error) {
	return u.CheckForUpdatesWithVersion(ctx, u.currentVersion)
}

// CheckForUpdatesWithVersion pings https://unboundmusic-two.vercel.app/api/version
// and compares the remote app version with the installed clientVersion.
func (u *Updater) CheckForUpdatesWithVersion(ctx context.Context, clientVersion string) (*UpdateInfo, error) {
	if clientVersion == "" {
		clientVersion = u.currentVersion
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.versionAPIURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "UnboundMusic-Android/"+clientVersion)
	req.Header.Set("Accept", "application/json")

	resp, err := u.httpClient.Do(req)
	if err != nil {
		return &UpdateInfo{
			CurrentVersion: clientVersion,
			LatestVersion:  clientVersion,
			HasUpdate:      false,
			ReleaseNotes:   "Offline or unable to reach update server.",
		}, nil
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return &UpdateInfo{
			CurrentVersion: clientVersion,
			LatestVersion:  clientVersion,
			HasUpdate:      false,
			ReleaseNotes:   "Up to date.",
		}, nil
	}

	var payload struct {
		Version string `json:"version"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil || strings.TrimSpace(payload.Version) == "" {
		return &UpdateInfo{
			CurrentVersion: clientVersion,
			LatestVersion:  clientVersion,
			HasUpdate:      false,
			ReleaseNotes:   "Up to date.",
		}, nil
	}

	remoteVer := strings.TrimSpace(payload.Version)
	hasUpdate := IsNewerVersion(remoteVer, clientVersion)
	notes := ""
	if hasUpdate {
		notes = fmt.Sprintf("Hey! Version %s is now available. Install the latest version of Unbound Music.", remoteVer)
	}

	return &UpdateInfo{
		CurrentVersion: clientVersion,
		LatestVersion:  remoteVer,
		HasUpdate:      hasUpdate,
		ReleaseNotes:   notes,
		DownloadURL:    u.downloadURL,
		PublishedAt:    time.Now().Format(time.RFC3339),
	}, nil
}
