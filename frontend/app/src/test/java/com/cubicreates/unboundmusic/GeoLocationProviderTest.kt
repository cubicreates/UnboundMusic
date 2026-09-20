package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.util.GeoLocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class GeoLocationProviderTest {

    @Before
    fun setUp() {
        GeoLocationProvider.resetCacheForTesting()
    }

    @Test
    fun testDefaultFallbackWhenContextIsNull() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            GeoLocationProvider.resetCacheForTesting()
            val code = GeoLocationProvider.getCountryCode(null)
            assertEquals("US", code)
        } finally {
            Locale.setDefault(originalLocale)
            GeoLocationProvider.resetCacheForTesting()
        }
    }

    @Test
    fun testLanguageCodeResolution() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val lang = GeoLocationProvider.getLanguageCode()
            assertEquals("en", lang)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testMemoizationCacheReusesResolvedValue() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            GeoLocationProvider.resetCacheForTesting()
            val first = GeoLocationProvider.getCountryCode(null)
            assertEquals("US", first)

            // Change default locale to UK, but since memoized, it should still return "US"
            Locale.setDefault(Locale.UK)
            val cached = GeoLocationProvider.getCountryCode(null)
            assertEquals("US", cached)

            // After reset, it picks up new locale
            GeoLocationProvider.resetCacheForTesting()
            val refreshed = GeoLocationProvider.getCountryCode(null)
            assertEquals("GB", refreshed)
        } finally {
            Locale.setDefault(originalLocale)
            GeoLocationProvider.resetCacheForTesting()
        }
    }
}
