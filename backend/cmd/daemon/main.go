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
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/ai"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/moods"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

func main() {
	port := flag.Int("port", 45731, "Port for localhost HTTP REST/IPC daemon")
	dataDir := flag.String("data-dir", "", "Base directory for SQLite database and cached assets")
	modelsPath := flag.String("models-dir", "", "Path to unpacked GGUF / ONNX edge AI model directory")
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

	db, err := database.Open(dbPath)
	if err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	ytClient := ytmusic.NewClient()
	exploreEng := ytmusic.NewExploreEngine(repo)
	moodEng := moods.NewEngine(exploreEng)
	aiRunner := ai.NewRunner(*modelsPath)

	daemon := NewDaemon(repo, exploreEng, moodEng, aiRunner, ytClient)

	httpServer := &http.Server{
		Addr:         fmt.Sprintf("127.0.0.1:%d", *port),
		Handler:      daemon.Routes(),
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 30 * time.Second,
	}

	// Trap OS interrupt signals for graceful shutdown
	stopChan := make(chan os.Signal, 1)
	signal.Notify(stopChan, os.Interrupt, syscall.SIGTERM)

	go func() {
		fmt.Printf("============================================================\n")
		fmt.Printf(" [UNBOUND ENGINE] Daemon Server Online\n")
		fmt.Printf(" Address:  http://127.0.0.1:%d\n", *port)
		fmt.Printf(" Database: %s\n", dbPath)
		fmt.Printf("============================================================\n")
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("Server error: %v\n", err)
		}
	}()

	<-stopChan
	fmt.Println("\n[UNBOUND ENGINE] Initiating graceful shutdown...")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		log.Fatalf("Shutdown error: %v", err)
	}
	fmt.Println("[UNBOUND ENGINE] Server shutdown complete.")
}
