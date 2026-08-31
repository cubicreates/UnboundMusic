/*
 * Package: storage
 * File: provisioner.go
 * Purpose: Unbound Root Directory Provisioner: automatically establishes and verifies the Unbound/ folder layout, isolating engine machinery in hidden Unbound/.backend/ and exposing visible Downloads/ and Playlists/ directories.
 * Subsystem: Storage Provisioning & File Architecture
 * Concurrency: Thread-safe filesystem directory creation and disk space querying.
 */

package storage

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

// DirectoryTree encapsulates the verified directory hierarchy and disk metrics.
type DirectoryTree struct {
	RootPath     string            `json:"root_path"`
	BackendPath  string            `json:"backend_path"`
	SQLitePath   string            `json:"sqlite_path"`
	ModelsPath   string            `json:"models_path"`
	CachePath    string            `json:"cache_path"`
	LogsPath     string            `json:"logs_path"`
	DownloadPath string            `json:"download_path"`
	MusicPath    string            `json:"music_path"`
	PlaylistPath string            `json:"playlist_path"`
	RecapPath    string            `json:"recap_path"`
	Directories  map[string]string `json:"directories"`
	IsReady      bool              `json:"is_ready"`
}

// Provisioner coordinates directory creation and path resolution.
type Provisioner struct {
	mu       sync.RWMutex
	baseRoot string
	tree     *DirectoryTree
}

// NewProvisioner initializes a storage provisioner anchored at the provided base root.
func NewProvisioner(baseRoot string) *Provisioner {
	if baseRoot == "" {
		home, err := os.UserHomeDir()
		if err != nil || home == "" {
			baseRoot = os.TempDir()
		} else {
			baseRoot = home
		}
	}

	return &Provisioner{
		baseRoot: baseRoot,
	}
}

// ProvisionLayout creates the standard Unbound directory hierarchy on disk.
func (p *Provisioner) ProvisionLayout() (*DirectoryTree, error) {
	p.mu.Lock()
	defer p.mu.Unlock()

	root := filepath.Join(p.baseRoot, "Unbound")
	backendRoot := filepath.Join(root, ".backend")

	sqliteDir := filepath.Join(backendRoot, "sqlite")
	modelsDir := filepath.Join(backendRoot, "models")
	cacheDir := filepath.Join(backendRoot, "cache")
	logsDir := filepath.Join(backendRoot, "logs")

	downloadDir := filepath.Join(root, "Downloads")
	musicDir := filepath.Join(root, "Music")
	playlistDir := filepath.Join(root, "Playlists")
	recapDir := filepath.Join(root, "Recaps")

	dirs := []string{
		root,
		backendRoot,
		sqliteDir,
		modelsDir,
		cacheDir,
		logsDir,
		downloadDir,
		musicDir,
		playlistDir,
		recapDir,
	}

	for _, d := range dirs {
		if err := os.MkdirAll(d, 0755); err != nil {
			return nil, fmt.Errorf("failed creating directory %s: %w", d, err)
		}
	}

	tree := &DirectoryTree{
		RootPath:     root,
		BackendPath:  backendRoot,
		SQLitePath:   sqliteDir,
		ModelsPath:   modelsDir,
		CachePath:    cacheDir,
		LogsPath:     logsDir,
		DownloadPath: downloadDir,
		MusicPath:    musicDir,
		PlaylistPath: playlistDir,
		RecapPath:    recapDir,
		Directories: map[string]string{
			"root":      root,
			".backend":  backendRoot,
			"sqlite":    sqliteDir,
			"models":    modelsDir,
			"cache":     cacheDir,
			"logs":      logsDir,
			"downloads": downloadDir,
			"music":     musicDir,
			"playlists": playlistDir,
			"recaps":    recapDir,
		},
		IsReady: true,
	}

	p.tree = tree
	return tree, nil
}

// GetTree returns the current provisioned directory tree.
func (p *Provisioner) GetTree() (*DirectoryTree, error) {
	p.mu.RLock()
	if p.tree != nil && p.tree.IsReady {
		tree := p.tree
		p.mu.RUnlock()
		return tree, nil
	}
	p.mu.RUnlock()

	return p.ProvisionLayout()
}
