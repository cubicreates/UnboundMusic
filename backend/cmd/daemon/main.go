/*
 * Package: main
 * File: main.go
 * Purpose: Embedded background daemon entry point providing local REST/IPC API on localhost:45731 for Unbound Music clients.
 * Subsystem: Localhost Daemon
 * Concurrency: Multi-threaded HTTP server with OS signal trapping for graceful shutdown.
 */

package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/server"
)

func main() {
	port := flag.Int("port", 45731, "Port for localhost HTTP REST/IPC daemon")
	dataDir := flag.String("data-dir", "", "Base directory for SQLite database and cached assets")
	flag.Parse()

	baseDir := *dataDir
	if baseDir == "" {
		homeDir, err := os.UserHomeDir()
		if err != nil {
			baseDir = os.TempDir()
		} else {
			baseDir = filepath.Join(homeDir, ".unbound_music")
		}
	}

	_ = os.MkdirAll(baseDir, 0755)
	dbPath := filepath.Join(baseDir, "unbound_music.db")

	cfg := server.Config{
		Port:           *port,
		DatabasePath:   dbPath,
		LibraryRoot:    filepath.Join(baseDir, "Library"),
		AppStorageRoot: baseDir,
	}

	srv, err := server.NewServer(cfg)
	if err != nil {
		log.Fatalf("Failed to initialize Unbound daemon server: %v", err)
	}

	// Trap OS interrupt signals for graceful shutdown
	stopChan := make(chan os.Signal, 1)
	signal.Notify(stopChan, os.Interrupt, syscall.SIGTERM)

	go func() {
		fmt.Printf("============================================================\n")
		fmt.Printf(" [UNBOUND ENGINE] Daemon Server Online\n")
		fmt.Printf(" Address:  http://127.0.0.1:%d\n", cfg.Port)
		fmt.Printf(" Database: %s\n", dbPath)
		fmt.Printf("============================================================\n")
		if err := srv.Start(); err != nil {
			log.Printf("Server stopped: %v\n", err)
		}
	}()

	<-stopChan
	fmt.Println("\n[UNBOUND ENGINE] Initiating graceful shutdown...")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Fatalf("Shutdown error: %v", err)
	}
	fmt.Println("[UNBOUND ENGINE] Server shutdown complete.")
}
