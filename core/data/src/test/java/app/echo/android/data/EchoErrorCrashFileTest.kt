package app.echo.android.data

import app.echo.android.model.error.EchoErrorDraft
import app.echo.android.model.error.EchoErrorSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoErrorCrashFileTest {
    @Test
    fun writeAndReadRoundTripThenDeletesFile() {
        val file = File.createTempFile("echo-error", ".json")
        EchoErrorCrashFile.write(
            file = file,
            throwable = IllegalStateException("disk full"),
            threadName = "main",
            nowEpochMs = 42L,
        )
        assertTrue(file.isFile)
        val draft = EchoErrorCrashFile.readAndDelete(file)
        assertEquals(EchoErrorSource.Crash, draft!!.source)
        assertEquals("disk full", draft.summary)
        assertEquals("main", draft.threadName)
        assertEquals(IllegalStateException::class.java.name, draft.throwableName)
        assertEquals(42L, draft.occurredAtEpochMs)
        assertEquals(42L, draft.firstOccurredAtEpochMs)
        assertTrue(draft.stackTrace!!.contains("IllegalStateException"))
        assertTrue(!file.exists())
    }

    @Test
    fun decodeRejectsBlankSummary() {
        assertNull(EchoErrorCrashFile.decode("""{"source":"Crash","summary":"  "}"""))
        assertNull(EchoErrorCrashFile.decode(null))
    }

    @Test
    fun writeDraftRedactsSensitiveSummary() {
        val file = File.createTempFile("echo-error", ".json")
        EchoErrorCrashFile.writeDraft(
            file,
            EchoErrorDraft(
                source = EchoErrorSource.Connect,
                summary = "pair failed https://u:p@pc.local/link?token=secret",
                occurredAtEpochMs = 7L,
            ),
        )
        val draft = EchoErrorCrashFile.readAndDelete(file)!!
        assertEquals(EchoErrorSource.Connect, draft.source)
        assertTrue(draft.summary.contains("<redacted>"))
        assertTrue(!draft.summary.contains("secret"))
        assertEquals(7L, draft.occurredAtEpochMs)
    }
}
