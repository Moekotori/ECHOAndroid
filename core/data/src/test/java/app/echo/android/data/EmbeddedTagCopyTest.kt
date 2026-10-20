package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class EmbeddedTagCopyTest {
    @Test fun exactBudgetAndEmptySourcesAreCopiedCompletely() {
        for (source in listOf(byteArrayOf(), ByteArray(8) { it.toByte() })) {
            val output = ByteArrayOutputStream()
            assertEquals(source.size.toLong(), copyEmbeddedTagSource(source.inputStream(), output, source.size.toLong()))
            assertArrayEquals(source, output.toByteArray())
        }
    }

    @Test fun oversizedUnknownSourceStopsAfterOneProbeByte() {
        val input = ByteArray(100).inputStream()
        val output = ByteArrayOutputStream()
        try {
            copyEmbeddedTagSource(input, output, 8)
            fail("Expected copy limit failure")
        } catch (_: IOException) {
            assertEquals(91, input.available())
            assertTrue(output.size() <= 8)
        }
    }
}
