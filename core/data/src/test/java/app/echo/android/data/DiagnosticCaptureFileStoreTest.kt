package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DiagnosticCaptureFileStoreTest {
    @Test fun rotationKeepsOrderAndOnlyOwnFilesWithinBudget() {
        val directory = Files.createTempDirectory("echo-capture-test").toFile()
        try {
            val unrelated = directory.resolve("private.txt").apply { writeText("not a diagnostic") }
            val store = DiagnosticCaptureFileStore(directory, 8)
            repeat(10) { store.append("event-$it\n") }
            assertEquals(4, store.files().size)
            assertTrue(store.files().sumOf { it.length() } <= 32)
            assertEquals(listOf("event-6", "event-7", "event-8", "event-9"), store.files().map { it.readText().trim() })
            store.clear()
            assertTrue(store.files().isEmpty())
            assertTrue(unrelated.exists())
        } finally { directory.listFiles().orEmpty().forEach { it.delete() }; directory.delete() }
    }
    @Test fun wallClockRollbackCannotExtendTheMonotonicDeadline() {
        assertFalse(DiagnosticCapturePolicy.expired(10, 10, 20, 20))
        assertTrue(DiagnosticCapturePolicy.expired(1, 20, 20, 20))
        assertTrue(DiagnosticCapturePolicy.expired(20, 1, 20, 20))
    }
}
