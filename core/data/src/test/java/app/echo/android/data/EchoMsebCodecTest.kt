package app.echo.android.data

import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test

class EchoMsebCodecTest {
    @Test fun settingsAndBackupRoundTripWithoutDroppingDisabledValues() {
        val state = EchoMsebPreset.Warm.applyTo(EchoMsebState())
        assertEquals(state, EchoMsebCodec.decode(EchoMsebCodec.encode(state)))
        val settings = EchoAppSettings(mseb = state).toBackupSettings()
        val restored = EchoBackupCodec.decode(EchoBackupCodec.encode(EchoBackupDocument(settings = settings)))
        assertEquals(state, restored.settings.mseb)
        assertNull(EchoBackupCodec.decode("{\"version\":1,\"settings\":{}}").settings.mseb)
    }

    @Test fun badAndOversizedValuesAreBoundedAndMissingControlsDefaultToZero() {
        assertEquals(EchoMsebState(), EchoMsebCodec.decode("not-json"))
        assertEquals(EchoMsebState(), EchoMsebCodec.decode(" ".repeat(2049)))
        val decoded = EchoMsebCodec.decode("{\"enabled\":true,\"values\":[1000,-1000,\"bad\"]}")
        assertEquals(10, decoded.values.size)
        assertEquals(listOf(100f, -100f, 0f), decoded.values.take(3))
        val state = EchoMsebState(true, listOf(Float.NaN, Float.POSITIVE_INFINITY, 200f)).normalized
        assertEquals(listOf(0f, 0f, 100f), state.values.take(3))
    }
}
