package app.echo.android.lyrics

import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LyricsInspectionInputTest {
    @Test
    fun acceptsFileExactlyAtLimitForSingleAndBulkReads() {
        val size = 2 * 1024 * 1024
        LibraryLyricsInspector.BoundedInput(ByteArray(size).inputStream(), EmptyCoroutineContext).use {
            assertEquals(size, it.readBytes().size)
            assertEquals(-1, it.read())
        }
        LibraryLyricsInspector.BoundedInput(ByteArray(size).inputStream(), EmptyCoroutineContext).use {
            assertEquals(size.toLong(), it.skip(size.toLong()))
            assertEquals(-1, it.read())
        }
    }

    @Test
    fun rejectsFileLargerThanLimit() {
        LibraryLyricsInspector.BoundedInput(ByteArray(2 * 1024 * 1024 + 1).inputStream(), EmptyCoroutineContext).use {
            assertThrows(IOException::class.java) { it.readBytes() }
        }
    }
}
