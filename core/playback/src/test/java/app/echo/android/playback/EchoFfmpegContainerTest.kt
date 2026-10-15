package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoFfmpegContainerTest {
    @Test fun recognizesOnlyTheFourNewContainers() {
        fun header(value: String) = value.toByteArray(Charsets.ISO_8859_1).copyOf(16)
        assertEquals("aiff", echoFfmpegContainer(header("FORM\u0000\u0000\u0000\u0010AIFF")))
        assertEquals("aiff", echoFfmpegContainer(header("FORM\u0000\u0000\u0000\u0010AIFC")))
        assertEquals("wv", echoFfmpegContainer(header("wvpk")))
        for (signature in listOf("\u007f\u00fe\u0080\u0001", "\u00fe\u007f\u0001\u0080",
            "\u001f\u00ff\u00e8\u0000", "\u00ff\u001f\u0000\u00e8")) {
            assertEquals("dts", echoFfmpegContainer(header(signature)))
        }
        assertEquals("asf", echoFfmpegContainer(header("\u0030\u0026\u00b2\u0075\u008e\u0066\u00cf\u0011\u00a6\u00d9\u0000\u00aa\u0000\u0062\u00ce\u006c")))
        for (signature in listOf("RIFF", "fLaC", "MAC ", "OggS", "DSD ", "FRM8", "ID3")) {
            assertNull(echoFfmpegContainer(header(signature)))
        }
        assertNull(echoFfmpegContainer(byteArrayOf(1, 2, 3)))
    }
}
