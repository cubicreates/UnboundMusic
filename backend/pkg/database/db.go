/*
 * Package: database
 * File: db.go
 * Purpose: Embedded SQLite database connection manager configured with WAL mode, pooling, and auto-migrations.
 * Subsystem: Permanent Storage & Vector Memory Bank
 * Concurrency: Thread-safe; database connection pool manages concurrent readers and single writer.
 */

package database

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"time"

	_ "modernc.org/sqlite"
)

// DB encapsulates the SQL database connection handle and transaction methods.
type DB struct {
	conn *sql.DB
	path string
}

// Open initializes or connects to the local SQLite database, applying pragmas and table schemas.
func Open(dbPath string) (*DB, error) {
	if dbPath == "" {
		dbPath = filepath.Join(os.TempDir(), "unbound_music.db")
	}

	dir := filepath.Dir(dbPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create database directory %s: %w", dir, err)
	}

	// SQLite connection string with WAL mode and busy timeout pragmas
	dsn := fmt.Sprintf("file:%s?_pragma=journal_mode(WAL)&_pragma=synchronous(NORMAL)&_pragma=busy_timeout(5000)&_pragma=cache_size(-64000)", dbPath)
	sqlDB, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open sqlite database at %s: %w", dbPath, err)
	}

	// Configure connection pooling
	sqlDB.SetMaxOpenConns(25)
	sqlDB.SetMaxIdleConns(10)
	sqlDB.SetConnMaxLifetime(10 * time.Minute)

	// Verify database connectivity
	if err := sqlDB.Ping(); err != nil {
		sqlDB.Close()
		return nil, fmt.Errorf("failed to ping sqlite database: %w", err)
	}

	db := &DB{
		conn: sqlDB,
		path: dbPath,
	}

	// Execute automatic schema migrations
	if err := db.migrate(); err != nil {
		db.Close()
		return nil, fmt.Errorf("database schema migration failed: %w", err)
	}

	return db, nil
}

// migrate executes initial DDL creation scripts.
func (d *DB) migrate() error {
	_, err := d.conn.Exec(SchemaDDL)
	return err
}

// Close gracefully closes open database connections.
func (d *DB) Close() error {
	if d.conn != nil {
		return d.conn.Close()
	}
	return nil
}

// Conn returns the underlying *sql.DB instance.
func (d *DB) Conn() *sql.DB {
	return d.conn
}
