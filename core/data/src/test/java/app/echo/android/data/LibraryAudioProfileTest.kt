package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAudioProfileTest {
    @Test fun containersStayUnconfirmedAndDsdIsSeparateFromPcm() {
        val profile = audioProfileFromBuckets(listOf(
            LibraryAudioProfileBucket("FLAC", "96000", 2, 2),
            LibraryAudioProfileBucket("FLAC", "44100", 3, 0),
            LibraryAudioProfileBucket("MP3", "44100", 4, 0),
            LibraryAudioProfileBucket("M4A", "48000", 2, 0),
            LibraryAudioProfileBucket("WAV", "UNKNOWN", 1, 0),
            LibraryAudioProfileBucket("DSD", "352800", 1, 1),
            LibraryAudioProfileBucket("DSD", "UNKNOWN", 1, 0),
        ))
        assertEquals(14, profile.trackCount)
        assertEquals(5, profile.losslessCount)
        assertEquals(4, profile.lossyCount)
        assertEquals(3, profile.unconfirmedCount)
        assertEquals(2, profile.dsdCount)
        assertEquals(2, profile.highSampleRateCount)
        assertEquals(5, profile.formats.first().count)
        assertEquals(1, profile.sampleRates.first { it.key == "DSD" }.count)
        assertEquals(2, profile.sampleRates.first { it.key == "UNKNOWN" }.count)
        assertEquals(profile.trackCount, profile.formats.sumOf { it.count })
        assertEquals(profile.trackCount, profile.sampleRates.sumOf { it.count })
    }

    @Test fun emptyLibraryHasNoQualityClaims() {
        val profile = audioProfileFromBuckets(emptyList())
        assertEquals(0, profile.trackCount)
        assertEquals(0, profile.losslessCount)
        assertEquals(emptyList<Any>(), profile.formats)
        assertEquals(emptyList<Any>(), profile.sampleRates)
    }

    @Test fun pcmRateAloneDoesNotClaimDsdEncoding() {
        val profile = audioProfileFromBuckets(listOf(LibraryAudioProfileBucket("WAV", "DSD", 1, 0)))
        assertEquals(0, profile.dsdCount)
        assertEquals("OTHER", profile.sampleRates.single().key)
    }

    @Test fun ttaAndTakAreLosslessButWave64RemainsAContainer() {
        val profile = audioProfileFromBuckets(listOf(
            LibraryAudioProfileBucket("TTA", "48000", 1, 0),
            LibraryAudioProfileBucket("TAK", "44100", 1, 0),
            LibraryAudioProfileBucket("W64", "96000", 1, 1),
        ))
        assertEquals(2, profile.losslessCount)
        assertEquals(1, profile.unconfirmedCount)
        assertEquals(setOf("TTA", "TAK", "W64"), profile.formats.map { it.key }.toSet())
    }
}
