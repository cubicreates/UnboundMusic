/*
 * Package: storage
 * File: storage_test.go
 * Purpose: Unit tests for Unbound storage provisioner and in-place virtual audio indexer.
 * Subsystem: Storage & Indexing Engine
 * Concurrency: Standard Go testing framework.
 */

package storage

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
)

func TestStorageProvisionerLayout(t *testing.T) {
	tempRoot := filepath.Join(os.TempDir(), "unbound_storage_test_root")
	defer os.RemoveAll(tempRoot)

	prov := NewProvisioner(tempRoot)
	tree, err := prov.ProvisionLayout()
	if err != nil {
		t.Fatalf("ProvisionLayout failed: %v", err)
	}

	if !tree.IsReady {
		t.Fatalf("expected tree to be ready")
	}

	// Verify hidden .backend directories
	if _, err := os.Stat(tree.BackendPath); os.IsNotExist(err) {
		t.Errorf("expected .backend to exist at %s", tree.BackendPath)
	}
	if _, err := os.Stat(tree.SQLitePath); os.IsNotExist(err) {
		t.Errorf("expected sqlite directory to exist at %s", tree.SQLitePath)
	}
	if _, err := os.Stat(tree.ModelsPath); os.IsNotExist(err) {
		t.Errorf("expected models directory to exist at %s", tree.ModelsPath)
	}

	// Verify visible user directories
	if _, err := os.Stat(tree.DownloadPath); os.IsNotExist(err) {
		t.Errorf("expected Downloads directory to exist at %s", tree.DownloadPath)
	}
	if _, err := os.Stat(tree.MusicPath); os.IsNotExist(err) {
		t.Errorf("expected Music directory to exist at %s", tree.MusicPath)
	}
	if _, err := os.Stat(tree.PlaylistPath); os.IsNotExist(err) {
		t.Errorf("expected Playlists directory to exist at %s", tree.PlaylistPath)
	}
}

func TestInPlaceVirtualIndexing(t *testing.T) {
	tempDir := filepath.Join(os.TempDir(), "unbound_indexer_test")
	_ = os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	// Create sample audio file
	sampleAudio := filepath.Join(tempDir, "test_song.mp3")
	_ = os.WriteFile(sampleAudio, make([]byte, 1024*500), 0644)

	dbPath := filepath.Join(tempDir, "test_db.db")
	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()
	repo := database.NewRepository(db)

	indexer := NewIndexer(repo)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	summary, err := indexer.IndexInPlace(ctx, tempDir)
	if err != nil {
		t.Fatalf("IndexInPlace failed: %v", err)
	}

	if summary.Mode != "VIRTUAL_IN_PLACE" {
		t.Errorf("expected mode VIRTUAL_IN_PLACE, got %s", summary.Mode)
	}
	if summary.TotalScanned == 0 {
		t.Errorf("expected at least 1 scanned file")
	}

	// Verify original file was NOT moved or deleted
	if _, err := os.Stat(sampleAudio); os.IsNotExist(err) {
		t.Errorf("sample audio should still exist in place, but was missing")
	}
}
