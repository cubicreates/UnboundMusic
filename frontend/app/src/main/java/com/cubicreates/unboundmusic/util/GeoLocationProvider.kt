/*
 * Package: com.cubicreates.unboundmusic.util
 * File: GeoLocationProvider.kt
 * Purpose: Resolves client geolocation and ISO country code for culturally-aware recommendations
 *          and regional Edge AI vibe search.
 * Subsystem: Client Intelligence & Localization
 */

package com.cubicreates.unboundmusic.util

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Locale

object GeoLocationProvider {
    private const val TAG = "GeoLocationProvider"

    @Volatile
    private var cachedCountryCode: String? = null

    /**
     * Resets the memoized country code cache (primarily for unit tests).
     */
    fun resetCacheForTesting() {
        cachedCountryCode = null
    }

    /**
     * Resolves the uppercase 2-letter ISO 3166-1 alpha-2 country code of the user.
     * Checks Cellular Network -> SIM Operator -> System Locale -> Default Fallback ("IN").
     */
    fun getCountryCode(context: Context?): String {
        cachedCountryCode?.let { return it }

        var resolved: String? = null

        try {
            if (context != null) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (tm != null) {
                    val networkCountry = tm.networkCountryIso
                    if (!networkCountry.isNullOrBlank() && networkCountry.length == 2) {
                        resolved = networkCountry.trim().uppercase(Locale.US)
                        Log.d(TAG, "Resolved country from NetworkCountryIso: $resolved")
                    }

                    if (resolved == null) {
                        val simCountry = tm.simCountryIso
                        if (!simCountry.isNullOrBlank() && simCountry.length == 2) {
                            resolved = simCountry.trim().uppercase(Locale.US)
                            Log.d(TAG, "Resolved country from SimCountryIso: $resolved")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading telephony country: ${e.message}")
        }

        if (resolved == null) {
            try {
                val localeCountry = Locale.getDefault().country
                if (!localeCountry.isNullOrBlank() && localeCountry.length == 2) {
                    resolved = localeCountry.trim().uppercase(Locale.US)
                    Log.d(TAG, "Resolved country from Locale: $resolved")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading locale country: ${e.message}")
            }
        }

        val finalCode = resolved ?: "IN"
        cachedCountryCode = finalCode
        Log.d(TAG, "Final resolved country code: $finalCode")
        return finalCode
    }

    /**
     * Resolves the lowercase 2-letter ISO 639-1 language code (e.g., "en", "hi", "es").
     */
    fun getLanguageCode(): String {
        return try {
            val lang = Locale.getDefault().language
            if (!lang.isNullOrBlank()) lang.trim().lowercase(Locale.US) else "en"
        } catch (e: Exception) {
            "en"
        }
    }
}
