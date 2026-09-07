/*
 * Package: ytmusic
 * File: cipher_test.go
 * Purpose: Unit tests for dynamic YouTube player cipher operations (swap, splice, reverse) and JS AST parsing.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit testing.
 */

package ytmusic

import (
	"testing"
)

// TestApplyCipherOps verifies that individual cipher operations behave according to YouTube specifications.
func TestApplyCipherOps(t *testing.T) {
	initialSig := "ABCDEF"

	// 1. Reverse: "ABCDEF" -> "FEDCBA"
	res := ApplyCipherOps(initialSig, []CipherOp{
		{Type: OpReverse},
	})
	if res != "FEDCBA" {
		t.Errorf("expected 'FEDCBA', got %q", res)
	}

	// 2. Swap(2): swap index 0 and 2 -> "CBA" in "ABC"
	resSwap := ApplyCipherOps("ABCDEF", []CipherOp{
		{Type: OpSwap, Param: 2},
	})
	if resSwap != "CBADEF" {
		t.Errorf("expected 'CBADEF', got %q", resSwap)
	}

	// 3. Splice(2): drop first 2 runes -> "CDEF"
	resSplice := ApplyCipherOps("ABCDEF", []CipherOp{
		{Type: OpSplice, Param: 2},
	})
	if resSplice != "CDEF" {
		t.Errorf("expected 'CDEF', got %q", resSplice)
	}

	// 4. Combined sequential pipeline: Swap(3) -> Reverse -> Splice(1)
	// "ABCDEF" -> swap(3) -> "DBCBEF"? (A swapped with D: "DBC A EF") -> reverse -> "FE A CBD" -> splice(1) -> "E A CBD"
	pipeline := []CipherOp{
		{Type: OpSwap, Param: 3}, // "DBC A EF"
		{Type: OpReverse},        // "FE A CBD"
		{Type: OpSplice, Param: 1},
	}
	combined := ApplyCipherOps("ABCDEF", pipeline)
	if len(combined) != 5 {
		t.Errorf("expected length 5 after splice(1), got %d (%q)", len(combined), combined)
	}
}

// TestParsePlayerCipherJS validates parsing real YouTube player JS snippets into executable operations.
func TestParsePlayerCipherJS(t *testing.T) {
	jsSnippet := `
var vR = {
	kE: function(a, b) {
		var c = a[0];
		a[0] = a[b % a.length];
		a[b % a.length] = c
	},
	wS: function(a) {
		a.reverse()
	},
	o9: function(a, b) {
		a.splice(0, b)
	}
};
function wR(a) {
	a = a.split("");
	vR.wS(a, 51);
	vR.kE(a, 17);
	vR.o9(a, 3);
	return a.join("")
}
`
	ops, err := ParsePlayerCipherJS(jsSnippet)
	if err != nil {
		t.Fatalf("failed parsing player JS cipher: %v", err)
	}

	if len(ops) != 3 {
		t.Fatalf("expected 3 operations, got %d", len(ops))
	}

	if ops[0].Type != OpReverse {
		t.Errorf("expected op[0] to be OpReverse, got %v", ops[0].Type)
	}
	if ops[1].Type != OpSwap || ops[1].Param != 17 {
		t.Errorf("expected op[1] to be OpSwap(17), got %v(%d)", ops[1].Type, ops[1].Param)
	}
	if ops[2].Type != OpSplice || ops[2].Param != 3 {
		t.Errorf("expected op[2] to be OpSplice(3), got %v(%d)", ops[2].Type, ops[2].Param)
	}
}
