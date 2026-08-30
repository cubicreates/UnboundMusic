/*
 * Package: vector
 * File: cosine.go
 * Purpose: High-performance pure Go vector math functions for cosine similarity and Euclidean distance calculation.
 * Subsystem: Offline Vector Search Engine
 * Concurrency: Stateless pure mathematical functions safe for concurrent execution across worker goroutines.
 */

package vector

import (
	"fmt"
	"math"
)

// CosineSimilarity computes the cosine similarity metric between two float32 vectors in [-1.0, 1.0].
func CosineSimilarity(a, b []float32) (float32, error) {
	if len(a) == 0 || len(b) == 0 {
		return 0, fmt.Errorf("vector cannot be empty")
	}
	if len(a) != len(b) {
		return 0, fmt.Errorf("vector dimension mismatch: len(a)=%d, len(b)=%d", len(a), len(b))
	}

	var dotProduct float64
	var normA float64
	var normB float64

	for i := 0; i < len(a); i++ {
		valA := float64(a[i])
		valB := float64(b[i])
		dotProduct += valA * valB
		normA += valA * valA
		normB += valB * valB
	}

	if normA == 0 || normB == 0 {
		return 0, nil // Zero vector has zero similarity
	}

	similarity := dotProduct / (math.Sqrt(normA) * math.Sqrt(normB))
	return float32(similarity), nil
}

// EuclideanDistance calculates the straight-line distance between two float32 vectors.
func EuclideanDistance(a, b []float32) (float32, error) {
	if len(a) == 0 || len(b) == 0 {
		return 0, fmt.Errorf("vector cannot be empty")
	}
	if len(a) != len(b) {
		return 0, fmt.Errorf("vector dimension mismatch: len(a)=%d, len(b)=%d", len(a), len(b))
	}

	var sumSq float64
	for i := 0; i < len(a); i++ {
		diff := float64(a[i] - b[i])
		sumSq += diff * diff
	}

	return float32(math.Sqrt(sumSq)), nil
}
