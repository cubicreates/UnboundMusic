/*
 * Package: database
 * File: search_history.go
 * Purpose: SQLite persistence for Guest & User search history ("Previous Searches") and TTL-bounded JSON search result cache.
 * Subsystem: Discovery & Offline Cache
 * Concurrency: Thread-safe database operations.
 */

package database

import (
	"context"
	"database/sql"
	"strings"
	"time"
	"unicode/utf8"
)

func normalizeSearchDomain(domain string) string {
	d := strings.ToLower(strings.TrimSpace(domain))
	if d == "podcast" || d == "podcasts" {
		return "podcasts"
	}
	return "music"
}

// RecordSearchQuery upserts a search query into search_history and prunes entries beyond the 50 most recent per domain.
func (r *Repository) RecordSearchQuery(ctx context.Context, domain, query string) error {
	if r == nil || r.db == nil || r.db.conn == nil {
		return nil
	}
	trimmed := strings.TrimSpace(query)
	if utf8.RuneCountInString(trimmed) < 2 {
		return nil
	}
	dom := normalizeSearchDomain(domain)
	norm := strings.ToLower(trimmed)
	now := time.Now().Unix()

	_, err := r.db.conn.ExecContext(ctx, `
		INSERT INTO search_history (query_norm, domain, display_query, search_count, last_searched_at)
		VALUES (?, ?, ?, 1, ?)
		ON CONFLICT(query_norm, domain) DO UPDATE SET
			display_query = excluded.display_query,
			search_count = search_count + 1,
			last_searched_at = excluded.last_searched_at;
	`, norm, dom, trimmed, now)
	if err != nil {
		return err
	}

	_, _ = r.db.conn.ExecContext(ctx, `
		DELETE FROM search_history
		WHERE domain = ? AND query_norm NOT IN (
			SELECT query_norm FROM search_history
			WHERE domain = ?
			ORDER BY last_searched_at DESC
			LIMIT 50
		);
	`, dom, dom)
	return nil
}

// GetSearchHistory returns recent search queries for the given domain, optionally filtered by prefix/substring.
func (r *Repository) GetSearchHistory(ctx context.Context, domain, prefix string, limit int) ([]string, error) {
	if r == nil || r.db == nil || r.db.conn == nil {
		return []string{}, nil
	}
	if limit <= 0 {
		limit = 20
	}
	dom := normalizeSearchDomain(domain)
	trimmedPrefix := strings.ToLower(strings.TrimSpace(prefix))

	var rows *sql.Rows
	var err error
	if trimmedPrefix == "" {
		rows, err = r.db.conn.QueryContext(ctx, `
			SELECT display_query
			FROM search_history
			WHERE domain = ?
			ORDER BY last_searched_at DESC
			LIMIT ?;
		`, dom, limit)
	} else {
		rows, err = r.db.conn.QueryContext(ctx, `
			SELECT display_query
			FROM search_history
			WHERE domain = ? AND query_norm LIKE ?
			ORDER BY CASE WHEN query_norm LIKE ? THEN 0 ELSE 1 END, last_searched_at DESC
			LIMIT ?;
		`, dom, "%"+trimmedPrefix+"%", trimmedPrefix+"%", limit)
	}
	if err != nil {
		return []string{}, err
	}
	defer rows.Close()

	results := make([]string, 0, limit)
	for rows.Next() {
		var q string
		if err := rows.Scan(&q); err == nil && strings.TrimSpace(q) != "" {
			results = append(results, q)
		}
	}
	return results, nil
}

// DeleteSearchHistoryItem removes a single query from search_history for the given domain.
func (r *Repository) DeleteSearchHistoryItem(ctx context.Context, domain, query string) error {
	if r == nil || r.db == nil || r.db.conn == nil {
		return nil
	}
	norm := strings.ToLower(strings.TrimSpace(query))
	if norm == "" {
		return nil
	}
	dom := normalizeSearchDomain(domain)
	_, err := r.db.conn.ExecContext(ctx, `
		DELETE FROM search_history WHERE domain = ? AND query_norm = ?;
	`, dom, norm)
	return err
}

// ClearSearchHistory deletes all saved search queries for the given domain.
func (r *Repository) ClearSearchHistory(ctx context.Context, domain string) error {
	if r == nil || r.db == nil || r.db.conn == nil {
		return nil
	}
	dom := normalizeSearchDomain(domain)
	_, err := r.db.conn.ExecContext(ctx, `DELETE FROM search_history WHERE domain = ?;`, dom)
	return err
}

// GetCachedJSON retrieves non-expired cached JSON bytes from feed_cache.
func (r *Repository) GetCachedJSON(ctx context.Context, key string) ([]byte, bool) {
	if r == nil || r.db == nil || r.db.conn == nil || key == "" {
		return nil, false
	}
	now := time.Now().Unix()
	var data string
	err := r.db.conn.QueryRowContext(ctx, `
		SELECT data_json FROM feed_cache
		WHERE cache_key = ? AND expires_at > ?
		LIMIT 1;
	`, key, now).Scan(&data)
	if err != nil || data == "" {
		return nil, false
	}
	return []byte(data), true
}

// SetCachedJSON stores a JSON payload in feed_cache with the given TTL.
func (r *Repository) SetCachedJSON(ctx context.Context, key string, payload []byte, ttl time.Duration) {
	if r == nil || r.db == nil || r.db.conn == nil || key == "" || len(payload) == 0 {
		return
	}
	expiresAt := time.Now().Add(ttl).Unix()
	_, _ = r.db.conn.ExecContext(ctx, `
		INSERT INTO feed_cache (cache_key, data_json, expires_at)
		VALUES (?, ?, ?)
		ON CONFLICT(cache_key) DO UPDATE SET
			data_json = excluded.data_json,
			expires_at = excluded.expires_at;
	`, key, string(payload), expiresAt)
}
