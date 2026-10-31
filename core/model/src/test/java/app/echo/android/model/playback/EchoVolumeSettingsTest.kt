package app.echo.android.model.playback

import org.junit.Assert.*
import org.junit.Test

class EchoVolumeSettingsTest {
    @Test fun firstVisitKeepsCurrentAttenuationAndGain() {
        for (gain in listOf(-6f, 6f)) {
            val headphones = EchoVolumeSettings(levelDb = -30f, gainDb = gain, rememberPerDevice = true)
                .forDevice("bluetooth:address:aa")
            val speaker = headphones.forDevice("speaker")
            assertEquals(-30f, speaker.levelDb, 0f)
            assertEquals(gain, speaker.gainDb, 0f)
            assertEquals(headphones.combinedGainDb(), speaker.combinedGainDb(), 0f)
            assertEquals(headphones.muted, speaker.muted)
            assertEquals(EchoVolumeDeviceLevel(-30f, gain), speaker.deviceLevels["speaker"])
        }
    }

    @Test fun deviceMemoryIsOffByDefaultAndOffModeDoesNotReadOrUpdateDeviceRecords() {
        assertFalse(EchoVolumeSettings().rememberPerDevice)
        val remembered = EchoVolumeSettings(-18f, 3f, rememberPerDevice = true).forDevice("speaker")
            .forDevice("usb:1:2").copy(levelDb = -25f, gainDb = -4f).rememberDevice()
        val shared = remembered.copy(rememberPerDevice = false).normalized
        assertNull(shared.deviceKey)
        assertEquals(shared, shared.forDevice("speaker"))
        assertEquals(-25f, shared.forDevice("speaker").levelDb, 0f)
        assertEquals(-4f, shared.forDevice("speaker").gainDb, 0f)
        val edited = shared.copy(levelDb = -10f, gainDb = 2f).rememberDevice()
        assertEquals(remembered.deviceLevels, edited.deviceLevels)
    }

    @Test fun deviceSwitchRestoresBothLevelsAndKeepsGlobalControls() {
        val speaker = EchoVolumeSettings(-18f, 3f, -1f, 0.1f, true, rememberPerDevice = true).forDevice("speaker")
        val headphones = speaker.forDevice("bluetooth:address:aa").copy(levelDb = -25f, gainDb = -4f)
        val returned = headphones.forDevice("speaker")
        assertEquals(-18f, returned.levelDb, 0f)
        assertEquals(3f, returned.gainDb, 0f)
        assertEquals(0.1f, returned.stepDb, 0f)
        assertEquals(-1f, returned.limitDb, 0f)
        assertTrue(returned.muted)
        val restoredHeadphones = returned.forDevice("bluetooth:address:aa")
        assertEquals(-25f, restoredHeadphones.levelDb, 0f)
        assertEquals(-4f, restoredHeadphones.gainDb, 0f)
        assertEquals(3f, returned.forDevice("usb:1:2").gainDb, 0f)
        var bounded = returned
        repeat(40) { bounded = bounded.forDevice("bluetooth:name:$it") }
        assertEquals(EchoVolumeDeviceIdentity.MaxDevices, bounded.deviceLevels.size)
        assertTrue(bounded.deviceLevels.containsKey(bounded.deviceKey))
    }

    @Test fun stableIdentitySeparatesSameNamedBluetoothAndIgnoresUsbPortChanges() {
        assertNotEquals(EchoVolumeDeviceIdentity.key(EchoOutputDeviceKind.Bluetooth, "Headphones", "AA:01"),
            EchoVolumeDeviceIdentity.key(EchoOutputDeviceKind.Bluetooth, "Headphones", "AA:02"))
        assertEquals(EchoVolumeDeviceIdentity.key(EchoOutputDeviceKind.Usb, "DAC", "port1", 1, 2),
            EchoVolumeDeviceIdentity.key(EchoOutputDeviceKind.Usb, "Renamed DAC", "port2", 1, 2))
        assertEquals("wired:name:Headphones", EchoVolumeDeviceIdentity.key(EchoOutputDeviceKind.Wired, "Headphones", null))
        assertNull(EchoVolumeDeviceIdentity.key(EchoOutputDeviceKind.System, null, null))
        assertFalse(EchoVolumeDeviceIdentity.validKey("bluetooth:\ninvalid"))
    }

    @Test fun volumePreampAndReplayGainShareTheCeiling() {
        val volume = EchoVolumeSettings(levelDb = -6f, gainDb = 12f, limitDb = -2f)
        assertEquals(-2f, volume.combinedGainDb(3f), 0f)
        assertEquals(-6f, volume.combinedGainDb(-12f), 0f)
        assertEquals(30f, EchoVolumeSettings().combinedGainDb(30f), 0f)
        assertFalse(EchoVolumeSettings().changesSamples)
        assertTrue(EchoVolumeSettings(muted = true).changesSamples)
    }

    @Test fun invalidSettingsAreBoundedAndFineAdjustmentPreservesMute() {
        val volume = EchoVolumeSettings(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 9f).normalized
        assertEquals(EchoVolumeSettings(), volume)
        assertEquals(-60f, EchoVolumeSettings(levelDb = -99f).normalized.levelDb, 0f)
        assertEquals(12f, EchoVolumeSettings(gainDb = 99f).normalized.gainDb, 0f)
        val muted = EchoVolumeSettings(levelDb = -0.1f, stepDb = 0.5f, muted = true)
        assertEquals(0f, muted.adjusted(1).levelDb, 0f)
        assertTrue(muted.adjusted(1).muted)
        assertEquals(-60f, EchoVolumeSettings(levelDb = -60f).adjusted(-1).levelDb, 0f)
    }
}
