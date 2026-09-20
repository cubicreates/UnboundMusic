/*
 * Package: ai
 * File: regional_vibe_seeds.go
 * Purpose: Culturally authentic regional song anthologies mapped to emotional intents (India vs Global).
 * Subsystem: Edge AI Engine / Affective MIR
 * Concurrency: Thread-safe pure constants and lookup functions with zero heap allocation per call.
 */

package ai

import "strings"

// RegionalSeeds provides high-yield song seeds mapped to moods for a specific geographic region.
type RegionalSeeds struct {
	VictoryAnthems []string
	SleepAmbient   []string
	WorkoutHype    []string
	MelancholySad  []string
	PartyDance     []string
	RomanticLove   []string
	FocusStudy     []string
}

// IndianRegionalSeeds contains iconic Bollywood, Indian Pop, and classic victory and emotional tracks.
var IndianRegionalSeeds = RegionalSeeds{
	VictoryAnthems: []string{
		"Chak De India Sukhwinder Singh",
		"Zinda Bhaag Milkha Bhaag Siddharth Mahadevan",
		"Kar Har Maidaan Fateh Sanju Sukhwinder Singh",
		"Lakshya Shankar Mahadevan title track",
		"Jai Ho AR Rahman Slumdog Millionaire",
		"Lehra Do 83 Arijit Singh",
		"Naatu Naatu RRR MM Keeravani",
		"Sultan title track Sukhwinder Singh",
		"Dangal title track Daler Mehndi",
		"Brothers Anthem Ajay Atul",
		"Aashayein Iqbal KK",
		"Apna Time Aayega Gully Boy Ranveer Singh",
		"Badal Pe Paon Hai Hema Sardesai",
		"Vande Mataram Maa Tujhe Salaam AR Rahman",
	},
	SleepAmbient: []string{
		"Phir Le Aya Dil Barfi Arijit Singh",
		"Kun Faya Kun AR Rahman Rockstar",
		"Iktara Wake Up Sid Kavita Seth",
		"Moh Moh Ke Dhaage Monali Thakur",
		"Tum Se Hi Jab We Met Mohit Chauhan",
		"Taare Zameen Par title track Shankar Mahadevan",
		"Indian Bansuri Flute meditation relaxing sleep",
		"Peaceful Sitar Santur ambient sleep music",
		"Kabira Encore Arijit Singh Harshdeep Kaur",
		"Agar Tum Saath Ho flute acoustic instrumental",
	},
	WorkoutHype: []string{
		"Malhari Bajirao Mastani Vishal Dadlani",
		"Get Ready To Fight Baaghi Benny Dayal",
		"Ziddi Dil Mary Kom Vishal Dadlani",
		"Sher Aaya Sher Gully Boy Divine",
		"Dhaakad Dangal Raftaar",
		"Sultan title track gym motivation",
		"Jee Karda Badlapur Divya Kumar",
		"Aarambh Hai Prachand Piyush Mishra",
		"Bhaag Milkha Bhaag title track Rock version",
	},
	MelancholySad: []string{
		"Channa Mereya Arijit Singh Ae Dil Hai Mushkil",
		"Agar Tum Saath Ho Alka Yagnik Arijit Singh Tamasha",
		"Tujhe Bhula Diya KK Mohit Chauhan Anjaana Anjaani",
		"Hamari Adhuri Kahani Arijit Singh",
		"Bhula Dena Aashiqui 2 Mustafa Zahid",
		"Tadap Tadap Ke Is Dil Se KK",
		"Kabira Tochi Raina Rekha Bhardwaj",
		"Luka Chuppi Rang De Basanti Lata Mangeshkar AR Rahman",
	},
	PartyDance: []string{
		"Gallan Goodiyaan Dil Dhadakne Do",
		"Kala Chashma Baar Baar Dekho Badshah Neha Kakkar",
		"London Thumakda Queen Labh Janjua",
		"Ghungroo War Arijit Singh Shilpa Rao",
		"Badtameez Dil Yeh Jawaani Hai Deewani Benny Dayal",
		"Kar Gayi Chull Kapoor and Sons Badshah",
		"Abhi Toh Party Shuru Hui Hai Badshah",
	},
	RomanticLove: []string{
		"Tum Hi Ho Aashiqui 2 Arijit Singh",
		"Raataan Lambiyan Shershaah Jubin Nautiyal",
		"Kesariya Brahmastra Arijit Singh",
		"Pee Loon Once Upon a Time in Mumbaai Mohit Chauhan",
		"Zehnaseeb Hasee Toh Phasee Chinmayi",
		"Shayad Love Aaj Kal Arijit Singh",
	},
	FocusStudy: []string{
		"Indian classical sitar instrumental for study and focus",
		"Bollywood lofi chill beats to study to",
		"Peaceful flute ambient morning raga meditation",
		"Tabla and Tanpura soothing background drone",
	},
}

// LatinRegionalSeeds contains Spanish and Latin victory and emotional anthems for ES/MX/BR/LATAM.
var LatinRegionalSeeds = RegionalSeeds{
	VictoryAnthems: []string{
		"Shakira Waka Waka This Time for Africa",
		"Ricky Martin La Copa de la Vida The Cup of Life",
		"Daddy Yankee Limbo",
		"J Balvin Willy William Mi Gente",
		"Los del Rio Macarena",
	},
	SleepAmbient: []string{
		"Guitarra espanola relajante para dormir",
		"Musica acustica suave para descansar",
		"Lofi latino suave para relajarse",
	},
	WorkoutHype: []string{
		"Daddy Yankee Gasolina",
		"Don Omar Danza Kuduro Lucenzo",
		"Pitbull Fireball",
		"J Balvin Skrillex In Da Getto",
	},
	MelancholySad: []string{
		"Reik Ya Me Entere",
		"Camila Mientes",
		"Sin Bandera Entra En Mi Vida",
	},
	PartyDance: []string{
		"Luis Fonsi Daddy Yankee Despacito",
		"Farruko Pepas",
		"Bad Bunny Titi Me Pregunto",
	},
	RomanticLove: []string{
		"Sebastian Yatra No Hay Nadie Mas",
		"Carlos Vives Shakira La Bicicleta",
	},
	FocusStudy: []string{
		"Guitarra clasica espanola estudio",
		"Bossa nova chill instrumental relax",
	},
}

// GlobalWesternSeeds contains universally recognized rock, pop, and hip-hop victory and emotional anthems.
var GlobalWesternSeeds = RegionalSeeds{
	VictoryAnthems: []string{
		"Queen We Are The Champions",
		"Survivor Eye of the Tiger",
		"Eminem Lose Yourself",
		"Fort Minor Remember The Name",
		"The Script Hall of Fame",
		"Eminem Till I Collapse",
		"Macklemore Cant Hold Us",
		"Kanye West Stronger",
		"Imagine Dragons Believer",
		"Journey Dont Stop Believin",
		"Sia Unstoppable",
		"Fall Out Boy Centuries",
	},
	SleepAmbient: []string{
		"Marconi Union Weightless",
		"Deep sleep relaxing ambient music delta waves",
		"Calming sleep music peaceful piano rain",
		"Lofi hip hop beats to sleep relax to",
		"Brian Eno Music for Airports",
	},
	WorkoutHype: []string{
		"Eminem Till I Collapse",
		"Kanye West Power",
		"DMX X Gon Give It To Ya",
		"Roy Jones Jr Cant Be Touched",
		"Gym Phonk workout drift bass boost",
		"Disturbed Down With The Sickness",
	},
	MelancholySad: []string{
		"Adele Someone Like You",
		"Lewis Capaldi Someone You Loved",
		"Coldplay The Scientist",
		"Johnny Cash Hurt",
		"Sam Smith Stay With Me",
	},
	PartyDance: []string{
		"Dua Lipa Don't Start Now",
		"The Weeknd Blinding Lights",
		"Calvin Harris Summer",
		"David Guetta Titanium",
	},
	RomanticLove: []string{
		"Ed Sheeran Perfect",
		"John Legend All of Me",
		"Taylor Swift Lover",
	},
	FocusStudy: []string{
		"Lofi chill study beats to concentrate",
		"Ludovico Einaudi Nuvole Bianche piano",
		"Study beats lofi focus music",
	},
}

// GetRegionalSeeds returns the seed anthology for a given ISO country code (e.g., "IN", "US", "MX").
func GetRegionalSeeds(region string) RegionalSeeds {
	upper := strings.ToUpper(strings.TrimSpace(region))
	switch upper {
	case "IN", "IND", "INDIA":
		return IndianRegionalSeeds
	case "ES", "MX", "AR", "CO", "BR":
		return LatinRegionalSeeds
	default:
		return GlobalWesternSeeds
	}
}
