package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.AudioCategory
import com.cubicreates.unboundmusic.data.MediaStoreAudioBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioClassificationTest {

    @Test
    fun testWhatsAppVoiceNotesClassifiedAsMixedAudio() {
        val (category, isIdentified) = MediaStoreAudioBridge.classifyAudioCategory(
            rawPath = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/PTT-20241001-WA0001.opus",
            sourceFolder = "WhatsApp Voice Notes",
            title = "PTT-20241001-WA0001",
            artist = "Unknown Artist",
            album = "",
            durationMs = 15000L,
            hasCoverArt = false
        )
        assertEquals(AudioCategory.MIXED_AUDIO, category)
        assertFalse(isIdentified)
    }

    @Test
    fun testWhatsAppAudioForwardClassifiedAsMixedAudio() {
        val (category, isIdentified) = MediaStoreAudioBridge.classifyAudioCategory(
            rawPath = "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-20241002-WA0004.mp3",
            sourceFolder = "WhatsApp Audio",
            title = "AUD-20241002-WA0004",
            artist = "<unknown>",
            album = "",
            durationMs = 45000L,
            hasCoverArt = false
        )
        assertEquals(AudioCategory.MIXED_AUDIO, category)
        assertFalse(isIdentified)
    }

    @Test
    fun testRecordingsFolderClassifiedAsMixedAudio() {
        val (category, isIdentified) = MediaStoreAudioBridge.classifyAudioCategory(
            rawPath = "/storage/emulated/0/Recordings/Voice 002.m4a",
            sourceFolder = "Recordings",
            title = "Voice 002",
            artist = "Unknown Artist",
            album = "",
            durationMs = 95000L,
            hasCoverArt = false
        )
        assertEquals(AudioCategory.MIXED_AUDIO, category)
        assertFalse(isIdentified)
    }

    @Test
    fun testShortUnidentifiedClipClassifiedAsMixedAudio() {
        val (category, isIdentified) = MediaStoreAudioBridge.classifyAudioCategory(
            rawPath = "/storage/emulated/0/Notifications/ding.ogg",
            sourceFolder = "Notifications",
            title = "ding",
            artist = "",
            album = "",
            durationMs = 2500L,
            hasCoverArt = false
        )
        assertEquals(AudioCategory.MIXED_AUDIO, category)
        assertFalse(isIdentified)
    }

    @Test
    fun testLegitimateMusicTrackClassifiedAsMusic() {
        val (category, isIdentified) = MediaStoreAudioBridge.classifyAudioCategory(
            rawPath = "/storage/emulated/0/Music/Dua Lipa - Levitating.mp3",
            sourceFolder = "Music",
            title = "Levitating",
            artist = "Dua Lipa",
            album = "Future Nostalgia",
            durationMs = 203000L,
            hasCoverArt = true
        )
        assertEquals(AudioCategory.MUSIC, category)
        assertTrue(isIdentified)
    }

    @Test
    fun testUnboundDownloadedSongClassifiedAsMusic() {
        val (category, isIdentified) = MediaStoreAudioBridge.classifyAudioCategory(
            rawPath = "/storage/emulated/0/Download/Unbound/Coldplay - Yellow.mp3",
            sourceFolder = "Unbound Downloads",
            title = "Yellow",
            artist = "Coldplay",
            album = "Parachutes",
            durationMs = 269000L,
            hasCoverArt = true
        )
        assertEquals(AudioCategory.MUSIC, category)
        assertTrue(isIdentified)
    }
}
