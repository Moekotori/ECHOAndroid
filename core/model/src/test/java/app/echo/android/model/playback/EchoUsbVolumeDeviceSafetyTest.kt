package app.echo.android.model.playback

import org.junit.Assert.*
import org.junit.Test

class EchoUsbVolumeDeviceSafetyTest {
    @Test fun initialOutputAppliesSavedDeviceAttenuationBeforeTheAsyncBinding() {
        val previous = EchoUsbOutputOptions(volumeDeviceKey = "1:1", deviceVolumeSteps = mapOf("2:2" to 100)).withVolumeStep(290)
        assertEquals(EchoUsbFineVolume.gain(100), EchoUsbFineVolume.gainForDevice(previous, 2, 2), 0f)
        assertEquals(previous.withVolumeStep(50).volumeGain, EchoUsbFineVolume.gainForDevice(previous.withVolumeStep(50), 2, 2), 0f)
    }

    @Test fun staleUiSnapshotCannotIncreaseTheNewDeviceVolume() {
        val stale = EchoUsbOutputOptions(volumeDeviceKey = "1:1").withVolumeStep(300)
        val current = EchoUsbOutputOptions(volumeDeviceKey = "2:2", deviceVolumeSteps = mapOf("2:2" to 100)).withVolumeStep(100)
        val merged = EchoUsbFineVolume.mergeDeviceSnapshot(stale, current)
        assertEquals(current.volumeGain, merged.volumeGain, 0f)
        assertEquals(current.deviceVolumeSteps, merged.deviceVolumeSteps)
        assertEquals(current.volumeDeviceKey, merged.volumeDeviceKey)
    }
}
