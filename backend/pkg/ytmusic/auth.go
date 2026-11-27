/*
 * Package: ytmusic
 * File: auth.go
 * Purpose: Authentication signer and session cookie parser for YouTube Music InnerTube requests.
 * Subsystem: YouTube Integration Engine
 * Concurrency: Stateless utility functions, fully concurrent safe.
 */

package ytmusic

import (
	"crypto/sha1"
	"encoding/hex"
	"fmt"
	"strings"
	"time"
)

// ParseCookies parses a raw HTTP Cookie header string into key-value pairs.
func ParseCookies(rawCookie string) map[string]string {
	result := make(map[string]string)
	parts := strings.Split(rawCookie, ";")
	for _, part := range parts {
		trimmed := strings.TrimSpace(part)
		if trimmed == "" {
			continue
		}
		eqIdx := strings.Index(trimmed, "=")
		if eqIdx > 0 {
			k := strings.TrimSpace(trimmed[:eqIdx])
			v := strings.TrimSpace(trimmed[eqIdx+1:])
			result[k] = v
		}
	}
	return result
}

// GenerateSAPISIDHash computes the dynamic authorization signature required by YouTube Music InnerTube:
// Authorization: SAPISIDHASH <timestamp>_<sha1(timestamp + " " + sapisid + " " + origin)>
func GenerateSAPISIDHash(sapisid, origin string) (string, int64) {
	ts := time.Now().Unix()
	return fmt.Sprintf("SAPISIDHASH %d_%s", ts, computeSAPISIDDigest(ts, sapisid, origin)), ts
}

func computeSAPISIDDigest(ts int64, sapisid, origin string) string {
	payload := fmt.Sprintf("%d %s %s", ts, sapisid, origin)
	hasher := sha1.New()
	hasher.Write([]byte(payload))
	return hex.EncodeToString(hasher.Sum(nil))
}

// BuildCookieAuthorizationHeader builds the full FirstPartyAuth header including SAPISIDHASH,
// SAPISID1PHASH, and SAPISID3PHASH when present in the user's cookie jar.
func BuildCookieAuthorizationHeader(rawCookie, origin string) string {
	cookies := ParseCookies(rawCookie)
	sapisid := cookies["SAPISID"]
	p1 := cookies["__Secure-1PAPISID"]
	p3 := cookies["__Secure-3PAPISID"]
	if sapisid == "" {
		if p3 != "" {
			sapisid = p3
		} else if p1 != "" {
			sapisid = p1
		}
	}
	if sapisid == "" && p1 == "" && p3 == "" {
		return ""
	}

	ts := time.Now().Unix()
	parts := make([]string, 0, 3)
	if sapisid != "" {
		parts = append(parts, fmt.Sprintf("SAPISIDHASH %d_%s", ts, computeSAPISIDDigest(ts, sapisid, origin)))
	}
	if p1 != "" {
		parts = append(parts, fmt.Sprintf("SAPISID1PHASH %d_%s", ts, computeSAPISIDDigest(ts, p1, origin)))
	}
	if p3 != "" {
		parts = append(parts, fmt.Sprintf("SAPISID3PHASH %d_%s", ts, computeSAPISIDDigest(ts, p3, origin)))
	}
	return strings.Join(parts, " ")
}

// HasValidAuthCookie returns true when the cookie header carries Google/YouTube session tokens.
func HasValidAuthCookie(rawCookie string) bool {
	cookies := ParseCookies(rawCookie)
	return cookies["SAPISID"] != "" ||
		cookies["__Secure-1PAPISID"] != "" ||
		cookies["__Secure-3PAPISID"] != "" ||
		cookies["SID"] != "" ||
		cookies["__Secure-1PSID"] != ""
}
