package ytmusic

import (
	"strings"
	"testing"
)

func TestParseCookies(t *testing.T) {
	raw := "SID=abc123xyz; HSID=def456; SAPISID=secret_sapisid_token; __Secure-3PAPISID=secure_token"
	cookies := ParseCookies(raw)

	if cookies["SAPISID"] != "secret_sapisid_token" {
		t.Errorf("expected SAPISID 'secret_sapisid_token', got '%s'", cookies["SAPISID"])
	}
	if cookies["__Secure-3PAPISID"] != "secure_token" {
		t.Errorf("expected __Secure-3PAPISID 'secure_token', got '%s'", cookies["__Secure-3PAPISID"])
	}
	if cookies["SID"] != "abc123xyz" {
		t.Errorf("expected SID 'abc123xyz', got '%s'", cookies["SID"])
	}
}

func TestGenerateSAPISIDHash(t *testing.T) {
	sapisid := "test_sapisid_val"
	origin := "https://music.youtube.com"

	header, timestamp := GenerateSAPISIDHash(sapisid, origin)
	if !strings.HasPrefix(header, "SAPISIDHASH ") {
		t.Fatalf("expected header to start with 'SAPISIDHASH ', got '%s'", header)
	}
	if timestamp <= 0 {
		t.Fatalf("expected positive timestamp, got %d", timestamp)
	}
}
