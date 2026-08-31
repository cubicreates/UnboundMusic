/*
 * Package: gatekeeper
 * File: payload_test.go
 * Purpose: Unit tests for Zstandard tar payload compression, streaming decompression, and decompression speed verification.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using temporary files.
 */

package gatekeeper

import (
	"os"
	"path/filepath"
	"testing"
)

// TestZstdCompressAndDecompress tests lossless compression and fast decompression.
func TestZstdCompressAndDecompress(t *testing.T) {
	tempDir := t.TempDir()

	file1 := filepath.Join(tempDir, "smollm2_dummy.gguf")
	file2 := filepath.Join(tempDir, "mms_align_dummy.onnx")

	content1 := []byte("GGUF_MODEL_MOCK_DATA_WEIGHTS_FOR_TESTING_1234567890_ABCDEFGHIJ")
	content2 := []byte("ONNX_MMS_ALIGN_MODEL_MOCK_DATA_WEIGHTS_FOR_TESTING_0987654321")

	os.WriteFile(file1, content1, 0644)
	os.WriteFile(file2, content2, 0644)

	files := map[string]string{
		"smollm2.gguf": file1,
		"mms.onnx":     file2,
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

	if manifest.DecompressionMs > 5000 {
		t.Errorf("decompression took too long: %d ms", manifest.DecompressionMs)
	}

	// Verify exact content matches bit-for-bit
	read1, _ := os.ReadFile(filepath.Join(destDir, "smollm2.gguf"))
	if string(read1) != string(content1) {
		t.Errorf("decompressed file1 content mismatch")
	}

	read2, _ := os.ReadFile(filepath.Join(destDir, "mms.onnx"))
	if string(read2) != string(content2) {
		t.Errorf("decompressed file2 content mismatch")
	}
}
