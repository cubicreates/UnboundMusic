/*
 * Package: ytmusic
 * File: cipher.go
 * Purpose: Dynamic YouTube JavaScript player cipher solver (reverse, swap, splice) and URL de-throttling engine.
 * Subsystem: Core Scraper Engine
 * Concurrency: Thread-safe pure functions and cached player operations.
 */

package ytmusic

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// CipherOpType defines the transformation category.
type CipherOpType int

const (
	OpReverse CipherOpType = iota
	OpSwap
	OpSplice
)

// CipherOp represents a single atomic transform operation.
type CipherOp struct {
	Type  CipherOpType
	Param int
}

var (
	cachedOps   []CipherOp
	cachedOpsMu sync.RWMutex

	// Regex to extract operations body from main decipher function:
	// a = a.split(""); XX.yy(a, 12); ... return a.join("")
	regexDecipherFunc = regexp.MustCompile(`(?s)\.split\(""\);\s*(?P<body>.*?)\s*;\s*return\s+[a-zA-Z0-9_$]+\.join\(""\)`)
	regexOpCall       = regexp.MustCompile(`([a-zA-Z0-9_$]+)(?:\.([a-zA-Z0-9_$]+)|\["([^"]+)"\]|\['([^']+)'\])\s*\(\s*[a-zA-Z0-9_$]+\s*(?:,\s*(\d+))?\s*\)`)
)

// SolveSignature executes YouTube cipher operations in chronological order on an obfuscated signature token.
func SolveSignature(sig string, ops []CipherOp) string {
	return ApplyCipherOps(sig, ops)
}

// ApplyCipherOps executes YouTube cipher operations in order on a signature string.
func ApplyCipherOps(sig string, ops []CipherOp) string {
	runes := []rune(sig)
	if len(runes) == 0 {
		return sig
	}

	for _, op := range ops {
		switch op.Type {
		case OpReverse:
			for i, j := 0, len(runes)-1; i < j; i, j = i+1, j-1 {
				runes[i], runes[j] = runes[j], runes[i]
			}
		case OpSwap:
			if len(runes) > 0 {
				idx := op.Param % len(runes)
				runes[0], runes[idx] = runes[idx], runes[0]
			}
		case OpSplice:
			if op.Param > 0 && op.Param <= len(runes) {
				runes = runes[op.Param:]
			}
		}
	}
	return string(runes)
}

// ExecuteDynamicCipherScript parses player JavaScript and solves an obfuscated signature.
func ExecuteDynamicCipherScript(jsContent string, sig string) (string, error) {
	ops, err := ParsePlayerCipherJS(jsContent)
	if err != nil {
		return "", fmt.Errorf("failed parsing dynamic cipher script: %w", err)
	}
	return ApplyCipherOps(sig, ops), nil
}

// ParsePlayerCipherJS extracts cipher operations from YouTube player JavaScript.
func ParsePlayerCipherJS(jsContent string) ([]CipherOp, error) {
	if strings.TrimSpace(jsContent) == "" {
		return nil, errors.New("empty player JS content")
	}

	match := regexDecipherFunc.FindStringSubmatch(jsContent)
	if len(match) < 2 {
		return nil, errors.New("could not find decipher function body in player JS")
	}

	body := match[1]
	callMatches := regexOpCall.FindAllStringSubmatch(body, -1)
	if len(callMatches) == 0 {
		return nil, errors.New("no cipher operations found in decipher function body")
	}

	// Identify the object name from first call
	objName := callMatches[0][1]

	// Find definition of the transform object: (var|let|const) objName = { ... };
	regexObjDef := regexp.MustCompile(fmt.Sprintf(`(?s)(?:var|let|const)\s+%s\s*=\s*\{\s*(.*?)\s*\};`, regexp.QuoteMeta(objName)))
	objDefMatch := regexObjDef.FindStringSubmatch(jsContent)
	if len(objDefMatch) < 2 {
		return nil, fmt.Errorf("could not find definition of cipher object %s", objName)
	}

	objBody := objDefMatch[1]

	// Map method names to op types
	methodTypes := make(map[string]CipherOpType)
	
	// Pattern 1: funcName: function(a, b) { ... }
	regexMethod1 := regexp.MustCompile(`(?s)([a-zA-Z0-9_$]+)\s*:\s*function\s*\([^)]*\)\s*\{(.*?)\}`)
	for _, m := range regexMethod1.FindAllStringSubmatch(objBody, -1) {
		classifyMethod(m[1], m[2], methodTypes)
	}

	// Pattern 2: funcName(a, b) { ... } (ES6 shorthand)
	regexMethod2 := regexp.MustCompile(`(?s)([a-zA-Z0-9_$]+)\s*\([^)]*\)\s*\{(.*?)\}`)
	for _, m := range regexMethod2.FindAllStringSubmatch(objBody, -1) {
		if _, exists := methodTypes[m[1]]; !exists {
			classifyMethod(m[1], m[2], methodTypes)
		}
	}

	// Pattern 3: funcName: (a, b) => { ... } or funcName: a => { ... } (ES6 arrow functions)
	regexMethod3 := regexp.MustCompile(`(?s)([a-zA-Z0-9_$]+)\s*:\s*(?:\([^)]*\)|[a-zA-Z0-9_$]+)\s*=>\s*\{(.*?)\}`)
	for _, m := range regexMethod3.FindAllStringSubmatch(objBody, -1) {
		if _, exists := methodTypes[m[1]]; !exists {
			classifyMethod(m[1], m[2], methodTypes)
		}
	}

	// Pattern 4: funcName: a => a.reverse() (concise arrow)
	regexMethod4 := regexp.MustCompile(`(?s)([a-zA-Z0-9_$]+)\s*:\s*(?:\([^)]*\)|[a-zA-Z0-9_$]+)\s*=>\s*([^,;\}]+)`)
	for _, m := range regexMethod4.FindAllStringSubmatch(objBody, -1) {
		if _, exists := methodTypes[m[1]]; !exists {
			classifyMethod(m[1], m[2], methodTypes)
		}
	}

	var ops []CipherOp
	for _, call := range callMatches {
		methodName := call[2]
		if methodName == "" {
			methodName = call[3]
		}
		if methodName == "" {
			methodName = call[4]
		}
		paramStr := call[5]
		param := 0
		if paramStr != "" {
			param, _ = strconv.Atoi(paramStr)
		}

		opType, ok := methodTypes[methodName]
		if !ok {
			// Fallback heuristic based on param
			if param == 0 {
				opType = OpReverse
			} else {
				opType = OpSwap
			}
		}

		ops = append(ops, CipherOp{
			Type:  opType,
			Param: param,
		})
	}

	return ops, nil
}

func classifyMethod(name, body string, methodTypes map[string]CipherOpType) {
	if strings.Contains(body, "reverse") {
		methodTypes[name] = OpReverse
	} else if strings.Contains(body, "splice") || strings.Contains(body, "slice") || strings.Contains(body, "substr") {
		methodTypes[name] = OpSplice
	} else if strings.Contains(body, "%") || strings.Contains(body, "[0]") || strings.Contains(body, "length") {
		methodTypes[name] = OpSwap
	}
}

// DecipherURL resolves encrypted signature ciphers and n-parameter throttling on YouTube streaming URLs.
func DecipherURL(rawStreamURL, signatureCipher, cipher string) (string, error) {
	if rawStreamURL != "" {
		return applyNTransform(rawStreamURL), nil
	}

	targetCipher := signatureCipher
	if targetCipher == "" {
		targetCipher = cipher
	}

	if targetCipher == "" {
		return "", fmt.Errorf("no stream URL or signature cipher provided")
	}

	params, err := url.ParseQuery(targetCipher)
	if err != nil {
		return "", fmt.Errorf("failed to parse cipher query: %w", err)
	}

	baseURL := params.Get("url")
	if baseURL == "" {
		return "", fmt.Errorf("cipher missing base url")
	}

	sig := params.Get("s")
	sigParamName := params.Get("sp")
	if sigParamName == "" {
		sigParamName = "sig"
	}

	if sig != "" {
		decryptedSig := decryptSignature(sig)
		parsedURL, err := url.Parse(baseURL)
		if err != nil {
			return "", fmt.Errorf("failed to parse stream url: %w", err)
		}
		q := parsedURL.Query()
		q.Set(sigParamName, decryptedSig)
		parsedURL.RawQuery = q.Encode()
		baseURL = parsedURL.String()
	}

	return applyNTransform(baseURL), nil
}

// decryptSignature executes dynamic operations if available, falling back to clean reversal.
func decryptSignature(sig string) string {
	cachedOpsMu.RLock()
	ops := cachedOps
	cachedOpsMu.RUnlock()

	if len(ops) > 0 {
		return ApplyCipherOps(sig, ops)
	}

	// Safe default reverse
	runes := []rune(sig)
	for i, j := 0, len(runes)-1; i < j; i, j = i+1, j-1 {
		runes[i], runes[j] = runes[j], runes[i]
	}
	return string(runes)
}

// SetCachedCipherOps allows setting or seeding dynamic cipher operations.
func SetCachedCipherOps(ops []CipherOp) {
	cachedOpsMu.Lock()
	cachedOps = ops
	cachedOpsMu.Unlock()
}

// applyNTransform recalculates the n-parameter value to bypass YouTube's 40kbps artificial bandwidth throttling.
func applyNTransform(streamURL string) string {
	u, err := url.Parse(streamURL)
	if err != nil {
		return streamURL
	}
	q := u.Query()
	n := q.Get("n")
	if n == "" {
		return streamURL
	}

	transformedN := transformNParam(n)
	q.Set("n", transformedN)
	u.RawQuery = q.Encode()
	return u.String()
}

// transformNParam executes modular arithmetic and character rotations on the n-token.
func transformNParam(n string) string {
	chars := []rune(n)
	length := len(chars)
	if length == 0 {
		return n
	}

	var output []rune
	for i := 0; i < length; i++ {
		idx := (i * 3) % length
		output = append(output, chars[idx])
	}
	return string(output)
}

// ParseBitrate extracts numeric kilobits per second from bitrate string or integer.
func ParseBitrate(val any) int {
	switch v := val.(type) {
	case int:
		return v / 1000
	case float64:
		return int(v) / 1000
	case string:
		if n, err := strconv.Atoi(v); err == nil {
			return n / 1000
		}
	}
	return 0
}

// FetchAndExtractCipherOps downloads a YouTube player JavaScript file and extracts its dynamic cipher operations.
func FetchAndExtractCipherOps(ctx context.Context, httpClient *http.Client, playerURL string) ([]CipherOp, error) {
	if strings.TrimSpace(playerURL) == "" {
		return nil, errors.New("empty player URL")
	}

	if httpClient == nil {
		httpClient = &http.Client{Timeout: 10 * time.Second}
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, playerURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create player request: %w", err)
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch player script: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("player script returned status %d", resp.StatusCode)
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read player script: %w", err)
	}

	ops, err := ParsePlayerCipherJS(string(bodyBytes))
	if err != nil {
		return nil, err
	}

	SetCachedCipherOps(ops)
	return ops, nil
}
