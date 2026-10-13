package app.echo.android.data

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class EchoApeMetadataTest {
    @Test fun headerSuppliesDurationWithoutPlatformCodec() {
        val b = ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN)
        b.put("MAC ".toByteArray()).putShort(3990).putShort(0)
            .putInt(52).putInt(24).putInt(8).putInt(0).putInt(100).putInt(0).putInt(0).put(ByteArray(16))
            .putShort(2000).putShort(0).putInt(294_912).putInt(44_100).putInt(2)
            .putShort(16).putShort(2).putInt(44_100)
        val info = EchoApeMetadata.read(ByteArrayInputStream(b.array()))!!
        assertEquals(44_100, info.sampleRateHz)
        assertEquals((294_912L + 44_100) * 1000 / 44_100, info.durationMs)
        b.putInt(72, 0)
        assertNull(EchoApeMetadata.read(ByteArrayInputStream(b.array())))
        assertNull(EchoApeMetadata.read(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
    }
}
