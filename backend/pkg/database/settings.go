/*
 * Package: database
 * File: settings.go
 * Purpose: Data Access Object for application preferences, DSP parameters, and custom user equalizer presets.
 * Subsystem: Permanent Storage
 * Concurrency: Thread-safe SQLite repository operations using context.Context.
 */

package database

import (
	"context"
	"database/sql"
	"time"
)

// UserEqPreset models a personalized equalizer curve and spatial DSP configuration.
type UserEqPreset struct {
	ID             int64  `json:"id"`
	Name           string `json:"name"`
	BandLevelsJSON string `json:"band_levels_json"`
	BassBoost      int    `json:"bass_boost"`
	Virtualizer    int    `json:"virtualizer"`
}

// GetSetting retrieves a stored configuration value by key.
func (r *Repository) GetSetting(ctx context.Context, key string) (string, error) {
	query := `SELECT value FROM app_settings WHERE key = ? LIMIT 1;`
	var val string
	err := r.db.conn.QueryRowContext(ctx, query, key).Scan(&val)
	if err != nil {
		if err == sql.ErrNoRows {
			return "", nil
		}
		return "", err
	}
	return val, nil
}

// SetSetting inserts or updates an application configuration key-value pair.
func (r *Repository) SetSetting(ctx context.Context, key, value string) error {
	query := `
		INSERT INTO app_settings (key, value, updated_at)
		VALUES (?, ?, ?)
		ON CONFLICT(key) DO UPDATE SET
			value = excluded.value,
			updated_at = excluded.updated_at;
	`
	_, err := r.db.conn.ExecContext(ctx, query, key, value, time.Now().Unix())
	return err
}

// GetAllSettings returns all currently stored configuration key-value pairs as a map.
func (r *Repository) GetAllSettings(ctx context.Context) (map[string]string, error) {
	query := `SELECT key, value FROM app_settings;`
	rows, err := r.db.conn.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	settings := make(map[string]string)
	for rows.Next() {
		var k, v string
		if err := rows.Scan(&k, &v); err != nil {
			return nil, err
		}
		settings[k] = v
	}
	return settings, rows.Err()
}

// SaveUserEqPreset saves or updates a personalized equalizer preset.
func (r *Repository) SaveUserEqPreset(ctx context.Context, preset UserEqPreset) error {
	query := `
		INSERT INTO user_eq_presets (name, band_levels_json, bass_boost, virtualizer)
		VALUES (?, ?, ?, ?)
		ON CONFLICT(name) DO UPDATE SET
			band_levels_json = excluded.band_levels_json,
			bass_boost = excluded.bass_boost,
			virtualizer = excluded.virtualizer;
	`
	_, err := r.db.conn.ExecContext(ctx, query, preset.Name, preset.BandLevelsJSON, preset.BassBoost, preset.Virtualizer)
	return err
}

// GetUserEqPresets returns all stored custom equalizer presets.
func (r *Repository) GetUserEqPresets(ctx context.Context) ([]UserEqPreset, error) {
	query := `SELECT id, name, band_levels_json, bass_boost, virtualizer FROM user_eq_presets ORDER BY name ASC;`
	rows, err := r.db.conn.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var presets []UserEqPreset
	for rows.Next() {
		var p UserEqPreset
		if err := rows.Scan(&p.ID, &p.Name, &p.BandLevelsJSON, &p.BassBoost, &p.Virtualizer); err != nil {
			return nil, err
		}
		presets = append(presets, p)
	}
	return presets, rows.Err()
}

// DeleteUserEqPreset removes a custom equalizer preset by name.
func (r *Repository) DeleteUserEqPreset(ctx context.Context, name string) error {
	query := `DELETE FROM user_eq_presets WHERE name = ?;`
	_, err := r.db.conn.ExecContext(ctx, query, name)
	return err
}
