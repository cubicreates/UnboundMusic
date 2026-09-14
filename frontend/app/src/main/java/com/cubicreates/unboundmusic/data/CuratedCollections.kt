/*
 * Package: com.cubicreates.unboundmusic.data
 * File: CuratedCollections.kt
 * Purpose: Curated playlist definitions and fallback metadata for moods & featured vibes.
 */

package com.cubicreates.unboundmusic.data

data class CuratedCollection(
    val key: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val fallbackQuery: String
)

object CuratedCollections {
    val CHILL = CuratedCollection(
        key = "Chill",
        id = "RDCLAK5uy_kbcFDVGmcuTcnDZ-xW4ptx2_Fl8PnOEZ8",
        title = "Chill Vibes",
        subtitle = "Laid-back lo-fi beats, mellow rhythms & relaxing melodies",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDyHtO-O8nDnNQPztyPxEsNT5aiv6Ne6kZnA1tJ-cA9inwrVyr-ymBfuWGK_5T2hpME1iChe-AtYUdH5A2k0gu52Th9z-P-SGfeUJGMt0QY1siq7VZYjQXNILIioT8qz3eYxxEW89dD6F5_AItNOLnbpyL14HsmiFL-z52BXMaJHuFN93508aZZcq5yaLARGYwtG62_v-gnM094_6--dzSr_ivn2CEZqKQ4GW0i60d9WHgS5AavJJjPbw",
        fallbackQuery = "lofi hip hop chill beats to relax"
    )

    val WORKOUT = CuratedCollection(
        key = "Workout",
        id = "RDCLAK5uy_kh9O6E_T7tPq-z-2q_K_6JmR5Kx3bU",
        title = "Workout Motivation",
        subtitle = "High-tempo, driving beats to power your peak performance",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAxPq4HTJcAPDJ5WTMxwDZVLLFwUiZx5DgbqsTmf0B5t4jIAUTKg-dGOVZRNbQztZubhqaz3JQbkd9CVOxQ-6rob41tJIe-X32GNdvRAzJUiFDuG0It1Q_CCJmlGjSEQ-afJLXSzgXzsWse2BvmJ3tNW-oEvStkKxl2WNLCqo_P2xiewvvc-Rq5c6PUbVBnWE9BGD0MuEIOOOthTBaWlmopATeIdm9OWCOTgJAhAsKoGClCsJoiG2TA6w",
        fallbackQuery = "workout motivation gym gym music hits"
    )

    val FOCUS = CuratedCollection(
        key = "Focus",
        id = "RDCLAK5uy_n59E8K5b5K5b8b8_focus",
        title = "Deep Focus",
        subtitle = "Calm, ambient, and instrumental soundscapes for clarity",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuC6HgRBezlfnehGhlmz0QilxBSTBtoG15Yb31LV2I8mRPr0JYREfRTqrNbum9n9W2Y8_xNQMvZ9Fvlq3fyWNEdjTfkBLpclVNpWlxaEfXRq5Wy3TGsDDcjj0DES-UJAuBNwJesZFk1QTZVYtpLgrNH1hAqMzWlxDedNxf9liGccEz9TBIvqevKFBJ7lnBaYfW5yqJOWNEeLj3rfMpAP38fCnhH3h_WE-5Kt8N9S1zB8ov1L9YcxNzLI7g",
        fallbackQuery = "deep focus study ambient instrumental music"
    )

    val ENERGY = CuratedCollection(
        key = "Energy",
        id = "RDCLAK5uy_m78_EDM_Pop_Dance_Energy",
        title = "Pure Energy",
        subtitle = "High-voltage EDM, electrifying dance pop, and upbeat anthems",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDKpK3reCGF7uRjNRLT8yPfymrmAvFQF8kzgYqx65G6Krz3qNKNCkKUHFFEDIg9NN-so1gOBcx2gUFVdA6V6pmjvgpckBjz8VgTXtPjdOaaOmT4wVVJqtSCq9poX75WIgSgwA_BdzIyFH6kB_UOlfQI2I1LvVoC6BiTucxhf6WBzVf_iibEw_jl0a6AtpwDU8tN8adZ-DBbJhj48AduN4fKA374KbwHZmDmK2mGOKhYp11KAqcpuc92KA",
        fallbackQuery = "high energy dance edm pop party hits"
    )

    val NEON_NOIR = CuratedCollection(
        key = "Neon Noir",
        id = "RDCLAK5uy_synthwave_retrowave_cyberpunk",
        title = "Neon Noir",
        subtitle = "Moody synths, dark electro & late night cyberpunk driving",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAYApkR1WLZQ1hOJB95_iBd2_6cuBHZ5VbNOvQ_hcNKz3gsZLAuAA6yPer-cv4wpCYpLlw68Hxd1W5C7vYY2UC06lB5ekBMo_nNZokBGdAYqpVtQupurMBSPsqk4e8h0mZN8oEPMAwaAgWr7ERuusrXszfIgYH5lETzYbT9eVnm0PQnIvgH7KIfCGgn6dcFzlWxtoheMs68tYehJtQm41jdKTmPMk5DLyHD6t14YXR9Zny59FV8fN8pRw",
        fallbackQuery = "synthwave retrowave dark electro cyberpunk night drive"
    )

    val VELVET_RNB = CuratedCollection(
        key = "Velvet R&B",
        id = "RDCLAK5uy_neo_soul_chill_rnb_vibes",
        title = "Velvet R&B",
        subtitle = "Silky vocals, deep neo-soul grooves, and heavy 808s",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBSJnsYO276b6VZ7n7LFagIeKmKHHuG6IEVYjF_pjp2JIV8dHBs80dkrCjjG626oVAhRoT0pLENqVIKiLZqeF_xmuxrIfZS54cHPQBRIrOj3x6_R6QjYDWTeMDb8OwPV9OfoUaFvLymzUkf0ghmIl8TB3mcfe8aGHGD2jMsGY7s6Rz7nhFTn69aLj9L8qY1RIP1ose4cRhb7qkN1d2shozxVLqWbD_hqAa-k6OZvsBgtEqdBN832OB5WA",
        fallbackQuery = "velvet rnb neo soul slow jams r&b hits"
    )

    val MINIMAL_TECHNO = CuratedCollection(
        key = "Minimal Techno",
        id = "RDCLAK5uy_minimal_techno_deep",
        title = "Minimal Techno",
        subtitle = "Deep hypnotic basslines, crisp percussion & underground grooves",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAnosEC-gnWsCAmEWPnFxuHS2fKqzJpXbYP2Te8W67oJwj0Pr_tECi8sJ2HCNCOeT4n6WkRuO0OFttj8LL-oU0jw1jr2FJkFUEvQwR7V8c48MrfoIUsR3Ns8H6UEiOVxxPEZ4jXP4_7EFwVd3RF0HIFlEnVyjEZi0Gm6QbIwe0N7Oua6_D2FlOQpb5468cLAkpaD6eBJm3W0J5RNzKGmf4ute3p-m0Hq8_QA0HG-YdvdnPJ3rQlKAxHQ",
        fallbackQuery = "minimal techno deep melodic underground techno"
    )

    val ALL = listOf(CHILL, WORKOUT, FOCUS, ENERGY, NEON_NOIR, VELVET_RNB, MINIMAL_TECHNO)

    fun find(query: String): CuratedCollection? {
        val trimmed = query.trim()
        return ALL.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }
            ?: ALL.firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
            ?: ALL.firstOrNull { trimmed.contains(it.key, ignoreCase = true) }
    }
}
