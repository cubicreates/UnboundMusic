/*
 * Package: fingerprint
 * File: scanner.go
 * Purpose: Concurrent recursive filesystem scanner for discovering, inspecting, and ingesting local audio files.
 * Subsystem: Storage & Fingerprint Engine
 * Concurrency: Worker pool pattern using goroutines and channels for high-throughput scanning.
 */

package fingerprint

import (
	"context"
	"io/fs"
	"path/filepath"
	"strings"
	"sync"
)

// ScanSummary aggregates results from a recursive library scan.
type ScanSummary struct {
	TotalFilesScanned int              `json:"total_files_scanned"`
	MusicFilesCount   int              `json:"music_files_count"`
	NoiseFilesCount   int              `json:"noise_files_count"`
	AudioTracks       []*AudioMetadata `json:"audio_tracks"`
}

// ScanDirectory recursively searches a root path and inspects all audio files concurrently.
func ScanDirectory(ctx context.Context, rootDir string, numWorkers int) (*ScanSummary, error) {
	if numWorkers <= 0 {
		numWorkers = 8
	}

	fileChan := make(chan string, 100)
	resultChan := make(chan *AudioMetadata, 100)
	var wg sync.WaitGroup

	// Start worker pool
	for i := 0; i < numWorkers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for path := range fileChan {
				select {
				case <-ctx.Done():
					return
				default:
					meta, err := InspectAudio(path)
					if err == nil && meta != nil {
						resultChan <- meta
					}
				}
			}
		}()
	}

	// Closer goroutine
	go func() {
		wg.Wait()
		close(resultChan)
	}()

	// Feeder goroutine: Walk filesystem tree
	var walkErr error
	go func() {
		defer close(fileChan)
		walkErr = filepath.WalkDir(rootDir, func(path string, d fs.DirEntry, err error) error {
			if err != nil {
				return nil // Skip unreadable directories
			}

			// Skip hidden folders (.git, .gradle, etc.)
			if d.IsDir() {
				name := d.Name()
				if strings.HasPrefix(name, ".") || name == "node_modules" {
					return filepath.SkipDir
				}
				return nil
			}

			ext := strings.ToLower(filepath.Ext(path))
			if SupportedExtensions[ext] {
				select {
				case <-ctx.Done():
					return ctx.Err()
				case fileChan <- path:
				}
			}
			return nil
		})
	}()

	summary := &ScanSummary{}

	// Collect worker outputs
	for meta := range resultChan {
		summary.TotalFilesScanned++
		classification := ClassifyAudio(meta)
		if classification.IsMusic {
			summary.MusicFilesCount++
		} else {
			summary.NoiseFilesCount++
		}
		summary.AudioTracks = append(summary.AudioTracks, meta)
	}

	if walkErr != nil && walkErr != context.Canceled {
		return summary, walkErr
	}

	return summary, nil
}
