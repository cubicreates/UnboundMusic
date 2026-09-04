/*
 * Package: gatekeeper
 * File: payload.go
 * Purpose: In-engine Zstandard streaming decompressor that unpacks compressed AI model bundles on first boot.
 * Subsystem: Storage & Intelligence Gatekeeper
 * Concurrency: Thread-safe decompression; uses mutex file locks to prevent concurrent unpack race conditions.
 */

package gatekeeper

import (
	"archive/tar"
	"bytes"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/klauspost/compress/zstd"
)

var (
	unpackMutex sync.Mutex
	freeDiskSpaceFunc = getFreeDiskSpace

	// ErrInsufficientStorageSpace is returned when the target disk has less than 250 MB free space.
	ErrInsufficientStorageSpace = errors.New("insufficient storage space: at least 250 MB free required")
)

const (
	// MinPayloadFreeSpaceBytes defines the minimum free storage required for AI extraction (250 MB).
	MinPayloadFreeSpaceBytes = int64(250 * 1024 * 1024)
)

// PayloadManifest contains details of unpacked AI model files.
type PayloadManifest struct {
	DecompressedDir   string    `json:"decompressed_dir"`
	TotalFiles        int       `json:"total_files"`
	TotalBytes        int64     `json:"total_bytes"`
	DecompressionMs   int64     `json:"decompression_ms"`
	IsReady           bool      `json:"is_ready"`
	SmolLMModelPath   string    `json:"smollm_model_path"`
	MiniLMModelPath   string    `json:"minilm_model_path"`
	MMSAlignModelPath string    `json:"mms_align_model_path,omitempty"`
	ExtractedAt       time.Time `json:"extracted_at"`
}

// DecompressZstdTarStream takes a Zstd-compressed tar archive stream and extracts files into the destination directory.
func DecompressZstdTarStream(compressedData []byte, destDir string) (*PayloadManifest, error) {
	unpackMutex.Lock()
	defer unpackMutex.Unlock()

	start := time.Now()

	// 1. Verify available storage capacity before extracting (> 250 MB required)
	checkPath := destDir
	for {
		if _, err := os.Stat(checkPath); err == nil {
			break
		}
		parent := filepath.Dir(checkPath)
		if parent == checkPath || parent == "." {
			checkPath = os.TempDir()
			break
		}
		checkPath = parent
	}

	freeBytes, err := freeDiskSpaceFunc(checkPath)
	if err == nil && freeBytes > 0 && freeBytes < MinPayloadFreeSpaceBytes {
		return nil, ErrInsufficientStorageSpace
	}

	if err := os.MkdirAll(destDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create destination directory: %w", err)
	}

	decoder, err := zstd.NewReader(bytes.NewReader(compressedData))
	if err != nil {
		return nil, fmt.Errorf("failed to initialize zstd decoder: %w", err)
	}
	defer decoder.Close()

	tarReader := tar.NewReader(decoder)
	var totalFiles int
	var totalBytes int64
	var smolLMPath, miniLMPath string
	var createdFiles []string

	cleanup := func() {
		for _, f := range createdFiles {
			_ = os.Remove(f)
		}
	}

	for {
		header, err := tarReader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			cleanup()
			return nil, fmt.Errorf("error reading tar archive stream: %w", err)
		}

		targetPath := filepath.Join(destDir, header.Name)

		switch header.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(targetPath, 0755); err != nil {
				cleanup()
				return nil, fmt.Errorf("failed to create directory %s: %w", targetPath, err)
			}
		case tar.TypeReg:
			if err := os.MkdirAll(filepath.Dir(targetPath), 0755); err != nil {
				cleanup()
				return nil, fmt.Errorf("failed to create parent dir for %s: %w", targetPath, err)
			}

			outFile, err := os.OpenFile(targetPath, os.O_CREATE|os.O_RDWR|os.O_TRUNC, header.FileInfo().Mode())
			if err != nil {
				cleanup()
				return nil, fmt.Errorf("failed to create output file %s: %w", targetPath, err)
			}

			written, err := io.Copy(outFile, tarReader)
			outFile.Close()
			if err != nil {
				cleanup()
				return nil, fmt.Errorf("failed writing unpacked file %s: %w", targetPath, err)
			}

			createdFiles = append(createdFiles, targetPath)
			totalFiles++
			totalBytes += written

			if filepath.Ext(targetPath) == ".gguf" {
				smolLMPath = targetPath
			} else if filepath.Ext(targetPath) == ".onnx" {
				miniLMPath = targetPath
			}
		}
	}

	elapsed := time.Since(start).Milliseconds()

	return &PayloadManifest{
		DecompressedDir:   destDir,
		TotalFiles:        totalFiles,
		TotalBytes:        totalBytes,
		DecompressionMs:   elapsed,
		IsReady:           true,
		SmolLMModelPath:   smolLMPath,
		MiniLMModelPath:   miniLMPath,
		MMSAlignModelPath: miniLMPath,
		ExtractedAt:       time.Now(),
	}, nil
}

// CompressFilesToZstdTar archives multiple file paths into a single Zstd-compressed tar byte buffer.
func CompressFilesToZstdTar(files map[string]string) ([]byte, error) {
	var buf bytes.Buffer
	encoder, err := zstd.NewWriter(&buf, zstd.WithEncoderLevel(zstd.SpeedBestCompression))
	if err != nil {
		return nil, fmt.Errorf("failed to initialize zstd encoder: %w", err)
	}

	tarWriter := tar.NewWriter(encoder)

	for archiveName, srcPath := range files {
		fileInfo, err := os.Stat(srcPath)
		if err != nil {
			return nil, fmt.Errorf("failed to stat file %s: %w", srcPath, err)
		}

		header, err := tar.FileInfoHeader(fileInfo, fileInfo.Name())
		if err != nil {
			return nil, fmt.Errorf("failed to create tar header for %s: %w", srcPath, err)
		}
		header.Name = archiveName

		if err := tarWriter.WriteHeader(header); err != nil {
			return nil, fmt.Errorf("failed to write tar header: %w", err)
		}

		file, err := os.Open(srcPath)
		if err != nil {
			return nil, fmt.Errorf("failed to open source file %s: %w", srcPath, err)
		}

		if _, err := io.Copy(tarWriter, file); err != nil {
			file.Close()
			return nil, fmt.Errorf("failed writing file into tar: %w", err)
		}
		file.Close()
	}

	if err := tarWriter.Close(); err != nil {
		return nil, err
	}
	if err := encoder.Close(); err != nil {
		return nil, err
	}

	return buf.Bytes(), nil
}
