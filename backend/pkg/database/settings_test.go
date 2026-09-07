package database

import (
	"context"
	"path/filepath"
	"testing"
)

func TestAppSettingsAndEQPresets(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_settings.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := NewRepository(db)
	ctx := context.Background()

	// 1. Setting set & get
	if err := repo.SetSetting(ctx, "theme_accent", "neon_violet"); err != nil {
		t.Fatalf("failed to set setting: %v", err)
	}
	val, err := repo.GetSetting(ctx, "theme_accent")
	if err != nil || val != "neon_violet" {
		t.Fatalf("expected 'neon_violet', got '%s' (err: %v)", val, err)
	}

	allSettings, err := repo.GetAllSettings(ctx)
	if err != nil || allSettings["theme_accent"] != "neon_violet" {
		t.Fatalf("expected allSettings to contain theme_accent, got %+v", allSettings)
	}

	// 2. EQ Preset save & get
	preset := UserEqPreset{
		Name:           "Custom Heavy Bass",
		BandLevelsJSON: "[8, 6, 4, 1, 0, 0, 1, 2, 4, 6]",
		BassBoost:      750,
		Virtualizer:    300,
	}
	if err := repo.SaveUserEqPreset(ctx, preset); err != nil {
		t.Fatalf("failed to save eq preset: %v", err)
	}

	presets, err := repo.GetUserEqPresets(ctx)
	if err != nil || len(presets) != 1 {
		t.Fatalf("expected 1 preset, got %d (err: %v)", len(presets), err)
	}
	if presets[0].Name != "Custom Heavy Bass" || presets[0].BassBoost != 750 {
		t.Errorf("unexpected preset contents: %+v", presets[0])
	}

	// 3. Delete preset
	if err := repo.DeleteUserEqPreset(ctx, "Custom Heavy Bass"); err != nil {
		t.Fatalf("failed to delete preset: %v", err)
	}
	presets, _ = repo.GetUserEqPresets(ctx)
	if len(presets) != 0 {
		t.Fatalf("expected 0 presets after delete, got %d", len(presets))
	}
}
