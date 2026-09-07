/*
 * Package: lyrics
 * File: transliterate.go
 * Purpose: On-device pure Go phonetic Romanization engine for Japanese (Hepburn Romaji), Korean (Revised Romanization), and Indic (Devanagari) lyrics.
 * Subsystem: Lyrics & Typography Engine
 * Concurrency: Thread-safe stateless pure functions.
 */

package lyrics

import (
	"strings"
	"unicode"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// Japanese Kana to Romaji mappings
var kanaDigraphs = map[string]string{
	"きゃ": "kya", "きゅ": "kyu", "きょ": "kyo",
	"しゃ": "sha", "しゅ": "shu", "しょ": "sho",
	"ちゃ": "cha", "ちゅ": "chu", "ちょ": "cho",
	"にゃ": "nya", "にゅ": "nyu", "にょ": "nyo",
	"ひゃ": "hya", "ひゅ": "hyu", "ひょ": "hyo",
	"みゃ": "mya", "みゅ": "myu", "みょ": "myo",
	"りゃ": "rya", "りゅ": "ryu", "りょ": "ryo",
	"ぎゃ": "gya", "ぎゅ": "gyu", "ぎょ": "gyo",
	"じゃ": "ja", "じゅ": "ju", "じょ": "jo",
	"びゃ": "bya", "びゅ": "byu", "びょ": "byo",
	"ぴゃ": "pya", "ぴゅ": "pyu", "ぴょ": "pyo",
	"キャ": "kya", "キュ": "kyu", "キョ": "kyo",
	"シャ": "sha", "シュ": "shu", "ショ": "sho",
	"チャ": "cha", "チュ": "chu", "チョ": "cho",
	"ニャ": "nya", "ニュ": "nyu", "ニョ": "nyo",
	"ヒャ": "hya", "ヒュ": "hyu", "ヒョ": "hyo",
	"ミャ": "mya", "ミュ": "myu", "ミョ": "myo",
	"リャ": "rya", "リュ": "ryu", "リョ": "ryo",
	"ギャ": "gya", "ギュ": "gyu", "ギョ": "gyo",
	"ジャ": "ja", "ジュ": "ju", "ジョ": "jo",
	"ビャ": "bya", "ビュ": "byu", "ビョ": "byo",
	"ピャ": "pya", "ピュ": "pyu", "ピョ": "pyo",
}

var kanaSingle = map[rune]string{
	// Hiragana
	'あ': "a", 'い': "i", 'う': "u", 'え': "e", 'お': "o",
	'か': "ka", 'き': "ki", 'く': "ku", 'け': "ke", 'こ': "ko",
	'さ': "sa", 'し': "shi", 'す': "su", 'せ': "se", 'そ': "so",
	'た': "ta", 'ち': "chi", 'つ': "tsu", 'て': "te", 'と': "to",
	'な': "na", 'に': "ni", 'ぬ': "nu", 'ね': "ne", 'の': "no",
	'は': "ha", 'ひ': "hi", 'ふ': "fu", 'へ': "he", 'ほ': "ho",
	'ま': "ma", 'み': "mi", 'む': "mu", 'め': "me", 'も': "mo",
	'や': "ya", 'ゆ': "yu", 'よ': "yo",
	'ら': "ra", 'り': "ri", 'る': "ru", 'れ': "re", 'ろ': "ro",
	'わ': "wa", 'を': "wo", 'ん': "n",
	'が': "ga", 'ぎ': "gi", 'ぐ': "gu", 'げ': "ge", 'ご': "go",
	'ざ': "za", 'じ': "ji", 'ず': "zu", 'ぜ': "ze", 'ぞ': "zo",
	'だ': "da", 'ぢ': "ji", 'づ': "zu", 'で': "de", 'ど': "do",
	'ば': "ba", 'び': "bi", 'ぶ': "bu", 'べ': "be", 'ぼ': "bo",
	'ぱ': "pa", 'ぴ': "pi", 'ぷ': "pu", 'ぺ': "pe", 'ぽ': "po",

	// Katakana
	'ア': "a", 'イ': "i", 'ウ': "u", 'エ': "e", 'オ': "o",
	'カ': "ka", 'キ': "ki", 'ク': "ku", 'ケ': "ke", 'コ': "ko",
	'サ': "sa", 'シ': "shi", 'ス': "su", 'セ': "se", 'ソ': "so",
	'タ': "ta", 'チ': "chi", 'ツ': "tsu", 'テ': "te", 'ト': "to",
	'ナ': "na", 'ニ': "ni", 'ヌ': "nu", 'ネ': "ne", 'ノ': "no",
	'ハ': "ha", 'ヒ': "hi", 'フ': "fu", 'ヘ': "he", 'ホ': "ho",
	'マ': "ma", 'ミ': "mi", 'ム': "mu", 'メ': "me", 'モ': "mo",
	'ヤ': "ya", 'ユ': "yu", 'ヨ': "yo",
	'ラ': "ra", 'リ': "ri", 'ル': "ru", 'レ': "re", 'ロ': "ro",
	'ワ': "wa", 'ヲ': "wo", 'ン': "n",
	'ガ': "ga", 'ギ': "gi", 'グ': "gu", 'ゲ': "ge", 'ゴ': "go",
	'ザ': "za", 'ジ': "ji", 'ズ': "zu", 'ゼ': "ze", 'ゾ': "zo",
	'ダ': "da", 'ヂ': "ji", 'ヅ': "zu", 'デ': "de", 'ド': "do",
	'バ': "ba", 'ビ': "bi", 'ブ': "bu", 'ベ': "be", 'ボ': "bo",
	'パ': "pa", 'ピ': "pi", 'プ': "pu", 'ペ': "pe", 'ポ': "po",
}

// Korean Hangul Jamo tables (Revised Romanization of Korean)
var (
	hangulChoseong = []string{
		"g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
	}
	hangulJungseong = []string{
		"a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
	}
	hangulJongseong = []string{
		"", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t", "t", "ng", "t", "t", "k", "t", "p", "h",
	}
)

// Devanagari tables
var devanagariVowels = map[rune]string{
	'अ': "a", 'आ': "aa", 'इ': "i", 'ई': "ee", 'उ': "u", 'ऊ': "oo",
	'ऋ': "ri", 'ए': "e", 'ऐ': "ai", 'ओ': "o", 'औ': "au",
}

var devanagariConsonants = map[rune]string{
	'क': "k", 'ख': "kh", 'ग': "g", 'घ': "gh", 'ङ': "ng",
	'च': "ch", 'छ': "chh", 'ज': "j", 'झ': "jh", 'ञ': "ny",
	'ट': "t", 'ठ': "th", 'ड': "d", 'ढ': "dh", 'ण': "n",
	'त': "t", 'थ': "th", 'द': "d", 'ध': "dh", 'न': "n",
	'प': "p", 'फ': "ph", 'ब': "b", 'भ': "bh", 'म': "m",
	'य': "y", 'र': "r", 'ल': "l", 'व': "v",
	'श': "sh", 'ष': "sh", 'स': "s", 'ह': "h",
}

var devanagariMatras = map[rune]string{
	'ा': "aa", 'ि': "i", 'ी': "ee", 'ु': "u", 'ू': "oo",
	'ृ': "ri", 'े': "e", 'ै': "ai", 'ो': "o", 'ौ': "au",
}

// RomanizeText converts non-Latin scripts (Japanese, Korean, Devanagari) to phonetic Latin script.
func RomanizeText(text string) string {
	if text == "" {
		return ""
	}

	runes := []rune(text)
	var sb strings.Builder
	n := len(runes)

	for i := 0; i < n; i++ {
		r := runes[i]

		// 1. Japanese Kana
		if (r >= 0x3040 && r <= 0x309F) || (r >= 0x30A0 && r <= 0x30FF) {
			// Check for 2-rune digraph (e.g. きゃ, ちょ)
			if i+1 < n {
				pair := string([]rune{r, runes[i+1]})
				if rom, ok := kanaDigraphs[pair]; ok {
					sb.WriteString(rom)
					i++
					continue
				}
			}

			// Sokuon (っ / ッ) doubles next consonant
			if r == 'っ' || r == 'ッ' {
				if i+1 < n {
					nextR := runes[i+1]
					if nextRom, ok := kanaSingle[nextR]; ok && len(nextRom) > 0 {
						sb.WriteByte(nextRom[0])
						continue
					}
					// Digraph after sokuon
					if i+2 < n {
						nextPair := string([]rune{nextR, runes[i+2]})
						if nextRom, ok := kanaDigraphs[nextPair]; ok && len(nextRom) > 0 {
							sb.WriteByte(nextRom[0])
							continue
						}
					}
				}
			}

			// Chōonpu (ー) lengthens preceding vowel
			if r == 'ー' {
				lastStr := sb.String()
				if len(lastStr) > 0 {
					lastChar := lastStr[len(lastStr)-1]
					switch lastChar {
					case 'a':
						sb.WriteByte('a')
					case 'i':
						sb.WriteByte('i')
					case 'u':
						sb.WriteByte('u')
					case 'e':
						sb.WriteByte('e')
					case 'o':
						sb.WriteByte('u') // Japanese long o is standardly transcribed as ou
					}
				}
				continue
			}

			if rom, ok := kanaSingle[r]; ok {
				sb.WriteString(rom)
				continue
			}
		}

		// 2. Korean Hangul Syllables (AC00 - D7A3)
		if r >= 0xAC00 && r <= 0xD7A3 {
			code := int(r - 0xAC00)
			choseongIdx := code / 588
			jungseongIdx := (code % 588) / 28
			jongseongIdx := code % 28

			cho := hangulChoseong[choseongIdx]
			jung := hangulJungseong[jungseongIdx]
			jong := hangulJongseong[jongseongIdx]

			sb.WriteString(cho)
			sb.WriteString(jung)
			sb.WriteString(jong)
			continue
		}

		// 3. Devanagari (Indic)
		if r >= 0x0900 && r <= 0x097F {
			if v, ok := devanagariVowels[r]; ok {
				sb.WriteString(v)
				continue
			}
			if c, ok := devanagariConsonants[r]; ok {
				sb.WriteString(c)
				// Look ahead: if followed by virama (0x094D), do not add inherent 'a'
				if i+1 < n && runes[i+1] == 0x094D {
					i++ // Skip virama
					continue
				}
				// If followed by a matra (dependent vowel sign)
				if i+1 < n {
					if matra, ok := devanagariMatras[runes[i+1]]; ok {
						sb.WriteString(matra)
						i++ // Skip matra
						continue
					}
				}
				// Inherent 'a' handling with schwa deletion for word-final consonants
				isWordEnd := (i+1 == n || unicode.IsSpace(runes[i+1]) || unicode.IsPunct(runes[i+1]))
				isWordStart := (i == 0 || unicode.IsSpace(runes[i-1]) || unicode.IsPunct(runes[i-1]))
				if isWordEnd && !isWordStart {
					continue
				}
				sb.WriteString("a")
				continue
			}
			if r == 0x0902 || r == 0x0901 { // Anusvara / Chandrabindu
				sb.WriteString("n")
				continue
			}
		}

		// 4. Default: passthrough (Latin, punctuation, space, numbers)
		sb.WriteRune(r)
	}

	return sb.String()
}

// RomanizeLyrics enriches each lyric line with phonetic Romanization if non-Latin scripts are present.
func RomanizeLyrics(lines []models.LyricLine) []models.LyricLine {
	result := make([]models.LyricLine, len(lines))

	for i, line := range lines {
		result[i] = line
		romanized := RomanizeText(line.Text)
		if romanized != line.Text && hasPhoneticDifference(line.Text, romanized) {
			result[i].Romanized = romanized
		}
	}

	return result
}

// hasPhoneticDifference returns true if the text underwent actual phonetic transliteration.
func hasPhoneticDifference(original, romanized string) bool {
	if original == romanized {
		return false
	}
	// Check if original contains non-ASCII runes
	for _, r := range original {
		if r > unicode.MaxASCII {
			return true
		}
	}
	return false
}
