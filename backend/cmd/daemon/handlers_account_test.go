package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestAccountEndpoints(t *testing.T) {
	d := NewDaemon(nil, nil, nil, nil, nil)
	handler := d.Routes()

	// 1. GET status initial
	req := httptest.NewRequest(http.MethodGet, "/api/v1/account/status", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200 for GET /api/v1/account/status, got %d", rec.Code)
	}

	var statusResp map[string]interface{}
	if err := json.NewDecoder(rec.Body).Decode(&statusResp); err != nil {
		t.Fatalf("failed to decode status response: %v", err)
	}
	if statusResp["connected"] != false {
		t.Errorf("expected connected=false, got %v", statusResp["connected"])
	}

	// 2. POST sync with valid SAPISID cookie
	body, _ := json.Marshal(map[string]string{"cookie": "SAPISID=sample_sapisid_token"})
	req = httptest.NewRequest(http.MethodPost, "/api/v1/account/sync", bytes.NewReader(body))
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200 for POST /api/v1/account/sync, got %d", rec.Code)
	}

	// 3. GET status after sync
	req = httptest.NewRequest(http.MethodGet, "/api/v1/account/status", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	json.NewDecoder(rec.Body).Decode(&statusResp)
	if statusResp["connected"] != true {
		t.Errorf("expected connected=true after sync, got %v", statusResp["connected"])
	}

	// 4. GET liked tracks
	req = httptest.NewRequest(http.MethodGet, "/api/v1/account/liked", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200 for GET /api/v1/account/liked, got %d", rec.Code)
	}

	// 5. POST disconnect
	req = httptest.NewRequest(http.MethodPost, "/api/v1/account/disconnect", bytes.NewReader([]byte("{}")))
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200 for POST /api/v1/account/disconnect, got %d", rec.Code)
	}
}
