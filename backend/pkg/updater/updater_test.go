package updater

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestIsNewerVersion(t *testing.T) {
	cases := []struct {
		remote  string
		current string
		want    bool
	}{
		{"1.0.27.11.26", "1.0.27.11.26", false},
		{"1.0.28.11.26", "1.0.27.11.26", true},
		{"1.0.01.12.26", "1.0.27.11.26", true},
		{"1.0.01.01.27", "1.0.27.11.26", true},
		{"1.0.26.11.26", "1.0.27.11.26", false},
		{"2.0.0", "1.0.27.11.26", true},
		{"1.0.27.11.26", "2.0.0", false},
	}
	for _, tc := range cases {
		if got := IsNewerVersion(tc.remote, tc.current); got != tc.want {
			t.Errorf("IsNewerVersion(%q, %q) = %v, want %v", tc.remote, tc.current, got, tc.want)
		}
	}
}

func TestUpdaterAgainstMockVersionAPI(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"version":"1.0.28.11.26"}`))
	}))
	defer srv.Close()

	up := NewUpdater("1.0.27.11.26")
	up.versionAPIURL = srv.URL

	info, err := up.CheckForUpdatesWithVersion(context.Background(), "1.0.27.11.26")
	if err != nil {
		t.Fatalf("CheckForUpdatesWithVersion failed: %v", err)
	}
	if !info.HasUpdate || info.LatestVersion != "1.0.28.11.26" {
		t.Fatalf("expected HasUpdate=true for 1.0.28.11.26, got %+v", info)
	}

	sameInfo, err := up.CheckForUpdatesWithVersion(context.Background(), "1.0.28.11.26")
	if err != nil {
		t.Fatalf("CheckForUpdatesWithVersion same failed: %v", err)
	}
	if sameInfo.HasUpdate {
		t.Fatalf("expected HasUpdate=false when versions match, got %+v", sameInfo)
	}
}
