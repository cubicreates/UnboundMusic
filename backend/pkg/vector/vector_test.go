/*
 * Package: vector
 * File: vector_test.go
 * Purpose: Unit tests for vector math, cosine similarity, Euclidean distance, and Top-K index search.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package vector

import (
	"testing"
)

// TestCosineSimilarityIdentity tests that identical vectors have similarity 1.0.
func TestCosineSimilarityIdentity(t *testing.T) {
	v1 := []float32{1.0, 2.0, 3.0, 4.0}
	v2 := []float32{1.0, 2.0, 3.0, 4.0}

	sim, err := CosineSimilarity(v1, v2)
	if err != nil {
		t.Fatalf("CosineSimilarity failed: %v", err)
	}

	if sim < 0.999 || sim > 1.001 {
		t.Errorf("expected identity similarity ~1.0, got %f", sim)
	}
}

// TestCosineSimilarityOrthogonal tests that perpendicular vectors have similarity 0.0.
func TestCosineSimilarityOrthogonal(t *testing.T) {
	v1 := []float32{1.0, 0.0}
	v2 := []float32{0.0, 1.0}

	sim, err := CosineSimilarity(v1, v2)
	if err != nil {
		t.Fatalf("CosineSimilarity failed: %v", err)
	}

	if sim != 0.0 {
		t.Errorf("expected orthogonal similarity 0.0, got %f", sim)
	}
}

// TestVectorIndexTopK tests Top-K nearest neighbors ranking.
func TestVectorIndexTopK(t *testing.T) {
	idx := NewIndex(4)

	_ = idx.Insert("doc1", []float32{1.0, 0.0, 0.0, 0.0}, nil)
	_ = idx.Insert("doc2", []float32{0.9, 0.1, 0.0, 0.0}, nil)
	_ = idx.Insert("doc3", []float32{0.0, 1.0, 0.0, 0.0}, nil)

	query := []float32{1.0, 0.0, 0.0, 0.0}
	results, err := idx.SearchTopK(query, 2)
	if err != nil {
		t.Fatalf("SearchTopK failed: %v", err)
	}

	if len(results) != 2 {
		t.Fatalf("expected 2 results, got %d", len(results))
	}

	if results[0].ID != "doc1" || results[1].ID != "doc2" {
		t.Errorf("unexpected top-K order: %+v", results)
	}
}
