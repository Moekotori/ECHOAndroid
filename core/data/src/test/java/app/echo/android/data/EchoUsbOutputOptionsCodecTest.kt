package app.echo.android.data

import app.echo.android.model.backup.EchoBackupDocument
import app.echo.android.model.playback.EchoUsbBufferMode
import app.echo.android.model.playback.EchoUsbOutputOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoUsbOutputOptionsCodecTest {
    @Test fun preferencesAndBackupKeepManualFormatAndPausedConnectionPolicy() {
        val options = EchoUsbOutputOptions(96000, 24, 2, 60, EchoUsbBufferMode.Stable, false)
        assertEquals(options, EchoUsbOutputOptionsCodec.decode(EchoUsbOutputOptionsCodec.encode(options)))
        val document = EchoBackupDocument(settings = EchoAppSettings(usbOutputOptions = options).toBackupSettings())
        assertEquals(options, EchoBackupCodec.decode(EchoBackupCodec.encode(document)).settings.usbOutputOptions)
        assertEquals(EchoUsbOutputOptions(), EchoUsbOutputOptionsCodec.decode("invalid"))
    }
}
