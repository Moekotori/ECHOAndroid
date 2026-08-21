package app.echo.android.data

import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.OpraEqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerSettingsCodecTest {
    @Test
    fun roundTripsParametricFilters() {
        val filters = listOf(
            OpraEqBand(EchoEqFilterType.LowShelf, 105f, -1.4f, 0.7f, null),
            OpraEqBand(EchoEqFilterType.PeakDip, 7619f, 3.3f, 4.48f, null),
        )
        val restored = parseEqualizerFilters(formatEqualizerFilters(filters))
        assertEquals(2, restored.size)
        assertEquals(EchoEqFilterType.LowShelf, restored[0].type)
        assertEquals(105f, restored[0].frequencyHz, 0.01f)
        assertEquals(-1.4f, restored[0].gainDb, 0.01f)
        assertEquals(0.7f, restored[0].q!!, 0.001f)
        assertEquals(4.48f, restored[1].q!!, 0.001f)
    }

    @Test
    fun invalidPayloadBecomesEmptyFilterList() {
        assertTrue(parseEqualizerFilters(null).isEmpty())
        assertTrue(parseEqualizerFilters("").isEmpty())
        assertTrue(parseEqualizerFilters("not-json").isEmpty())
    }
}
