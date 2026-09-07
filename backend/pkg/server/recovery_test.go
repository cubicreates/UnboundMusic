/*
 * Package: server
 * File: recovery_test.go
 * Purpose: Unit tests verifying panic recovery middleware and goroutine crash isolation.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package server

import (
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

// TestRecoveryMiddlewareCatchesPanic verifies that handler panics return 500 without crashing the process.
func TestRecoveryMiddlewareCatchesPanic(t *testing.T) {
	panicHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		panic("simulated fatal nil pointer dereference")
	})

	wrapped := RecoveryMiddleware(panicHandler)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/test_panic", nil)
	w := httptest.NewRecorder()

	// This must not crash the test suite
	wrapped.ServeHTTP(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("expected HTTP 500 on panic, got %d", w.Code)
	}

	body := w.Body.String()
	if body == "" {
		t.Errorf("expected JSON error body on panic, got empty")
	}
}

// TestSafeGoIsolatesPanic verifies that background goroutines do not terminate the process when panicking.
func TestSafeGoIsolatesPanic(t *testing.T) {
	var wg sync.WaitGroup
	wg.Add(1)

	SafeGo("test_worker", func() {
		defer wg.Done()
		panic("simulated background worker panic")
	})

	// Wait with timeout
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// Success, panic was caught and wg.Done() executed
	case <-time.After(2 * time.Second):
		t.Fatalf("SafeGo timed out waiting for goroutine")
	}
}
