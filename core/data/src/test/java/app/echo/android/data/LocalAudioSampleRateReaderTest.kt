package app.echo.android.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalAudioSampleRateReaderTest {
    @Test fun knownMetadataSkipsExtractor() {
        assertEquals(96_000, sampleRateWithFallback({ 96_000 }, { error("Unexpected extra I/O") }))
    }

    @Test fun missingInvalidOrUnsupportedMetadataUsesFormatHeader() {
        listOf<Int?>(null, 0, -1).forEach { value ->
            assertEquals(44_100, sampleRateWithFallback({ value }, { 44_100 }))
        }
        assertEquals(48_000, sampleRateWithFallback({ throw IllegalArgumentException("Unsupported key") }, { 48_000 }))
        assertNull(sampleRateWithFallback({ null }, { 0 }))
        assertNull(sampleRateWithFallback({ null }, { throw IllegalStateException("Unsupported format") }))
    }

    @Test fun cancellationIsNeverTreatedAsMissingMetadata() {
        assertThrows(CancellationException::class.java) {
            sampleRateWithFallback({ throw CancellationException() }, { error("Cancelled") })
        }
        assertThrows(CancellationException::class.java) {
            sampleRateWithFallback({ null }, { throw CancellationException() })
        }
    }
}
