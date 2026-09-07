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
	payload := fmt.Sprintf("%d %s %s", ts, sapisid, origin)
	hasher := sha1.New()
	hasher.Write([]byte(payload))
	hashHex := hex.EncodeToString(hasher.Sum(nil))
	return fmt.Sprintf("SAPISIDHASH %d_%s", ts, hashHex), ts
}
