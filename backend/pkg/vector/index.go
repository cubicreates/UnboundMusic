/*
 * Package: vector
 * File: index.go
 * Purpose: In-memory pure Go nearest-neighbors vector index supporting Top-K similarity queries.
 * Subsystem: Offline Vector Search Engine
 * Concurrency: Thread-safe with sync.RWMutex protecting concurrent read searches and inserts.
 */

package vector

import (
	"fmt"
	"sort"
	"sync"
)

// Item represents an indexed vector with associated metadata.
type Item struct {
	ID       string                 `json:"id"`
	Vector   []float32              `json:"vector"`
	Metadata map[string]interface{} `json:"metadata,omitempty"`
}

// SearchResult holds the matched item ID and similarity score.
type SearchResult struct {
	ID         string                 `json:"id"`
	Score      float32                `json:"score"`
	Metadata   map[string]interface{} `json:"metadata,omitempty"`
}

// Index provides an in-memory vector index for cosine nearest neighbors.
type Index struct {
	mu        sync.RWMutex
	dimension int
	items     []Item
	itemMap   map[string]int
}

// NewIndex creates an empty vector index with a specified dimensionality (e.g. 128).
func NewIndex(dim int) *Index {
	if dim <= 0 {
		dim = 128
	}
	return &Index{
		dimension: dim,
		items:     make([]Item, 0, 100),
		itemMap:   make(map[string]int),
	}
}

// Insert adds or updates a vector in the index.
func (idx *Index) Insert(id string, vec []float32, meta map[string]interface{}) error {
	if id == "" {
		return fmt.Errorf("item ID cannot be empty")
	}
	if len(vec) != idx.dimension {
		return fmt.Errorf("vector dimension mismatch: expected %d, got %d", idx.dimension, len(vec))
	}

	idx.mu.Lock()
	defer idx.mu.Unlock()

	if pos, exists := idx.itemMap[id]; exists {
		idx.items[pos] = Item{ID: id, Vector: vec, Metadata: meta}
		return nil
	}

	idx.itemMap[id] = len(idx.items)
	idx.items = append(idx.items, Item{ID: id, Vector: vec, Metadata: meta})
	return nil
}

// SearchTopK retrieves the top K most similar items to the given query vector.
func (idx *Index) SearchTopK(query []float32, k int) ([]SearchResult, error) {
	if len(query) != idx.dimension {
		return nil, fmt.Errorf("query dimension mismatch: expected %d, got %d", idx.dimension, len(query))
	}
	if k <= 0 {
		k = 10
	}

	idx.mu.RLock()
	defer idx.mu.RUnlock()

	if len(idx.items) == 0 {
		return nil, nil
	}

	results := make([]SearchResult, 0, len(idx.items))
	for _, item := range idx.items {
		sim, err := CosineSimilarity(query, item.Vector)
		if err == nil {
			results = append(results, SearchResult{
				ID:       item.ID,
				Score:    sim,
				Metadata: item.Metadata,
			})
		}
	}

	// Sort descending by score
	sort.Slice(results, func(i, j int) bool {
		return results[i].Score > results[j].Score
	})

	if len(results) > k {
		results = results[:k]
	}

	return results, nil
}

// Count returns the number of vectors stored in the index.
func (idx *Index) Count() int {
	idx.mu.RLock()
	defer idx.mu.RUnlock()
	return len(idx.items)
}
