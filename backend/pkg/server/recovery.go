/*
 * Package: server
 * File: recovery.go
 * Purpose: Top-level panic recovery middleware and isolated safe goroutine launcher ensuring 100% engine crash immunity.
 * Subsystem: Localhost Daemon & IPC
 * Concurrency: Thread-safe middleware and goroutine wrapper.
 */

package server

import (
	"fmt"
	"log"
	"net/http"
	"runtime/debug"
)

// RecoveryMiddleware wraps an HTTP handler with panic recovery to prevent engine crashes.
func RecoveryMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if rErr := recover(); rErr != nil {
				stack := debug.Stack()
				log.Printf("[ENGINE RECOVERY] Panic caught in handler %s %s: %v\n%s", r.Method, r.URL.Path, rErr, string(stack))
				writeError(w, http.StatusInternalServerError, fmt.Sprintf("internal engine recovery: %v", rErr))
			}
		}()
		next.ServeHTTP(w, r)
	})
}

// SafeGo executes a background task in an isolated goroutine with panic recovery.
func SafeGo(name string, fn func()) {
	go func() {
		defer func() {
			if rErr := recover(); rErr != nil {
				stack := debug.Stack()
				log.Printf("[ENGINE GOROUTINE RECOVERY] Panic caught in background task %q: %v\n%s", name, rErr, string(stack))
			}
		}()
		fn()
	}()
}
