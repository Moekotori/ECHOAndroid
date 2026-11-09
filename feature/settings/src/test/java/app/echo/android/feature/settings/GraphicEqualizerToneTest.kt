package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphicEqualizerToneTest {
    @Test fun toneAdjustmentPreservesPresetDetailOutsideItsRange() {
        val gains = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Vocal)
        val bands = EchoEqualizerPresets.defaultBands(gains)
        val center = ToneBandIndices[2]
        val lower = ToneBandIndices[1]
        val upper = ToneBandIndices[3]

        val adjusted = adjustToneBand(bands, gains, 2, gains[center] + 3f)

        assertEquals(gains[center] + 3f, adjusted[center], 0.0001f)
        assertEquals(gains[center - 1] + 3f * (center - lower - 1) / (center - lower), adjusted[center - 1], 0.0001f)
        assertEquals(gains.take(lower + 1), adjusted.take(lower + 1))
        assertEquals(gains.drop(upper), adjusted.drop(upper))
        assertEquals(gains, adjustToneBand(bands, gains, 2, gains[center]))
    }

    @Test fun shelfEndsClampWithoutChangingOtherToneControls() {
        val bands = EchoEqualizerPresets.defaultBands()
        val gains = bands.map { if (it.index == ToneBandIndices.first()) 10f else 11f }
        val next = adjustToneBand(bands, gains, 0, 12f)

        assertEquals(12f, next.first(), 0f)
        assertEquals(12f, next[ToneBandIndices.first()], 0f)
        assertEquals(gains.drop(ToneBandIndices[1]), next.drop(ToneBandIndices[1]))
    }
}
