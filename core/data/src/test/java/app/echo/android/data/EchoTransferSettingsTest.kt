package app.echo.android.data

import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test

class EchoTransferSettingsTest {
    @Test fun msebRangeAndPersonalPresetsSurvivePortableBackup() {
        val preset = EchoMsebUserPreset("one", "Fine", List(10) { 75f })
        val settings = EchoAppSettings(mseb = EchoMsebState(adjustmentRange = EchoMsebAdjustmentRange.Fine), msebUserPresets = listOf(preset))
        val restored = EchoBackupCodec.decode(EchoBackupCodec.encode(EchoBackupDocument(settings = settings.toBackupSettings()))).settings
        assertEquals(settings.mseb, restored.mseb)
        assertEquals(listOf(preset), restored.msebUserPresets)
        assertEquals(EchoMsebAdjustmentRange.Wide, EchoMsebCodec.decode("{\"enabled\":true}").adjustmentRange)
    }

    @Test fun legacyPercentIsExactAndFineStepsRetainDeviceRecords() {
        val legacy = EchoUsbOutputOptionsCodec.decode("{\"volumePercent\":37}")
        assertEquals(0.37f, legacy.volumeGain, 0f)
        assertNull(legacy.volumeStep)
        assertTrue(EchoUsbFineVolume.gain(legacy.effectiveVolumeStep) <= legacy.volumeGain)
        val fine = legacy.withVolumeStep(211).copy(volumeDeviceKey = "12:34", deviceVolumeSteps = mapOf("12:34" to 211))
        assertEquals(fine.normalized, EchoUsbOutputOptionsCodec.decode(EchoUsbOutputOptionsCodec.encode(fine)))
        assertEquals(-17.8f, EchoUsbFineVolume.db(211), 0.001f)
        assertEquals(0f, fine.withVolumeStep(0).volumeGain, 0f)
        assertEquals(1f, fine.withVolumeStep(300).volumeGain, 0f)
        assertTrue(fine.volumeGain != fine.withVolumeStep(212).volumeGain)
    }
}
