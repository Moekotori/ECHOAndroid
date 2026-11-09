package app.echo.android.data

import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.playback.EchoUsbBufferMode
import app.echo.android.model.playback.EchoUsbOutputOptions
import app.echo.android.model.playback.EchoUsbVolumeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoUsbOutputOptionsCodecTest {
    @Test fun preferencesAndBackupIgnoreLegacyManualFormat() {
        val automatic = EchoUsbOutputOptions(
            volumePercent = 60,
            bufferMode = EchoUsbBufferMode.Stable,
            keepAliveOnPause = false,
            keepAliveByDevice = mapOf("123:456" to true, "789:1" to false),
            dopDeviceLimits = mapOf("123:456" to 11_289_600, "789:1" to 2_822_400),
        )
        val manual = automatic.copy(sampleRateHz = 96_000, bitDepth = 24, channelCount = 2)
        val encoded = EchoUsbOutputOptionsCodec.encode(automatic)
        assertEquals(encoded, EchoUsbOutputOptionsCodec.encode(manual))
        val legacy = JSONObject(encoded)
            .put("sampleRateHz", manual.sampleRateHz)
            .put("bitDepth", manual.bitDepth)
            .put("channelCount", manual.channelCount)
        assertEquals(automatic, EchoUsbOutputOptionsCodec.decode(legacy.toString()))

        val backup = JSONObject()
            .put("version", EchoBackupDocument.CurrentVersion)
            .put("settings", JSONObject().put("usbOutputOptions", legacy))
        assertEquals(automatic, EchoBackupCodec.decode(backup.toString()).settings.usbOutputOptions)
        assertEquals(EchoUsbOutputOptions(), EchoUsbOutputOptionsCodec.decode("invalid"))
        assertEquals(emptyMap<String, Int>(), EchoUsbOutputOptionsCodec.decode("{}").dopDeviceLimits)
    }

    @Test fun legacyHardwareAndResponsiveModesMigrateAndKeepThePauseDefault() {
        val options = EchoUsbOutputOptionsCodec.decode(
            """{"volumeMode":"hardware","bufferMode":"responsive","keepAliveOnPause":false}""",
        )
        assertEquals(EchoUsbOutputOptions(volumeMode = EchoUsbVolumeMode.Auto, keepAliveOnPause = false), options)
    }
}
