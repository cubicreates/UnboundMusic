/*
 * Package: gatekeeper
 * File: payload_test.go
 * Purpose: Unit tests for Zstandard tar payload compression, streaming decompression, speed verification, and storage threshold guards.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using temporary directories.
 */

package gatekeeper

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// TestCompressAndDecompressRoundTrip tests lossless compression and fast decompression of AI models.
func TestCompressAndDecompressRoundTrip(t *testing.T) {
	tempDir := t.TempDir()

	file1 := filepath.Join(tempDir, "smollm2_135m.gguf")
	file2 := filepath.Join(tempDir, "model_quantized.onnx")

	content1 := []byte("GGUF_MODEL_MOCK_DATA_WEIGHTS_FOR_TESTING_1234567890_ABCDEFGHIJ")
	content2 := []byte("ONNX_MINILM_EMBEDDING_MODEL_MOCK_DATA_WEIGHTS_FOR_TESTING_0987654321")

	if err := os.WriteFile(file1, content1, 0644); err != nil {
		t.Fatalf("failed to write mock file1: %v", err)
	}
	if err := os.WriteFile(file2, content2, 0644); err != nil {
		t.Fatalf("failed to write mock file2: %v", err)
	}

	files := map[string]string{
		"smollm2_135m.gguf":    file1,
		"model_quantized.onnx": file2,
	}

	compressedBytes, err := CompressFilesToZstdTar(files)
	if err != nil {
		t.Fatalf("CompressFilesToZstdTar failed: %v", err)
	}

	if len(compressedBytes) == 0 {
		t.Fatalf("compressed bytes is empty")
	}

	// Test Decompression
	destDir := filepath.Join(tempDir, "extracted")
	manifest, err := DecompressZstdTarStream(compressedBytes, destDir)
	if err != nil {
		t.Fatalf("DecompressZstdTarStream failed: %v", err)
	}

	if manifest.TotalFiles != 2 {
		t.Errorf("expected 2 files extracted, got %d", manifest.TotalFiles)
	}

	if manifest.SmolLMModelPath == "" || filepath.Ext(manifest.SmolLMModelPath) != ".gguf" {
		t.Errorf("expected valid SmolLMModelPath, got: %q", manifest.SmolLMModelPath)
	}

	if manifest.MiniLMModelPath == "" || filepath.Ext(manifest.MiniLMModelPath) != ".onnx" {
		t.Errorf("expected valid MiniLMModelPath, got: %q", manifest.MiniLMModelPath)
	}

	// Verify exact content matches bit-for-bit
	read1, err := os.ReadFile(manifest.SmolLMModelPath)
	if err != nil || string(read1) != string(content1) {
		t.Errorf("decompressed file1 content mismatch: %v", err)
	}

	read2, err := os.ReadFile(manifest.MiniLMModelPath)
	if err != nil || string(read2) != string(content2) {
		t.Errorf("decompressed file2 content mismatch: %v", err)
	}
}

// TestZstdCompressAndDecompress alias for backward compatibility.
func TestZstdCompressAndDecompress(t *testing.T) {
	TestCompressAndDecompressRoundTrip(t)
}

// TestDecompressionSpeed verifies that the streaming decompressor satisfies the < 800ms desktop threshold.
func TestDecompressionSpeed(t *testing.T) {
	tempDir := t.TempDir()

	// Generate 5 MB of synthetic repeating compressible weight data
	mockData := bytes.Repeat([]byte("0123456789ABCDEF0123456789ABCDEF"), 160000)
	srcFile := filepath.Join(tempDir, "synthetic_model.gguf")
	if err := os.WriteFile(srcFile, mockData, 0644); err != nil {
		t.Fatalf("failed to write synthetic model: %v", err)
	}

	files := map[string]string{
		"synthetic_model.gguf": srcFile,
	}

	compressedBytes, err := CompressFilesToZstdTar(files)
	if err != nil {
		t.Fatalf("CompressFilesToZstdTar failed: %v", err)
	}

	destDir := filepath.Join(tempDir, "bench_extracted")
	start := time.Now()
	manifest, err := DecompressZstdTarStream(compressedBytes, destDir)
	duration := time.Since(start)

	if err != nil {
		t.Fatalf("DecompressZstdTarStream failed: %v", err)
	}

	if manifest.TotalBytes != int64(len(mockData)) {
		t.Errorf("extracted size mismatch: expected %d, got %d", len(mockData), manifest.TotalBytes)
	}

	// Must be well under 800ms desktop target
	if duration > 800*time.Millisecond {
		t.Errorf("decompression took %v, exceeding 800ms threshold", duration)
	}
}

// TestInsufficientStorageAborts verifies that decompression safely refuses to proceed when disk space is below 250 MB.
func TestInsufficientStorageAborts(t *testing.T) {
	oldFunc := freeDiskSpaceFunc
	defer func() { freeDiskSpaceFunc = oldFunc }()

	// Mock low storage condition (50 MB available)
	freeDiskSpaceFunc = func(path string) (int64, error) {
		return 50 * 1024 * 1024, nil
	}

	tempDir := t.TempDir()
	destDir := filepath.Join(tempDir, "low_storage_target")

	manifest, err := DecompressZstdTarStream([]byte("mock_compressed_stream"), destDir)
	if err != ErrInsufficientStorageSpace {
		t.Fatalf("expected ErrInsufficientStorageSpace, got: %v (manifest: %+v)", err, manifest)
	}

	// Verify nothing was created in destination
	if _, err := os.Stat(destDir); !os.IsNotExist(err) {
		t.Errorf("expected destination directory not to exist when aborted, but it does")
	}
}
