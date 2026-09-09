package app.echo.android.model.error

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoErrorLogTest {
    @After
    fun uninstallSink() {
        EchoErrorLog.install(null)
        EchoErrorLog.setProcessInfo(null)
    }

    @Test
    fun recordIsNoOpWithoutSink() {
        EchoErrorLog.install(null)
        EchoErrorLog.record(EchoErrorSource.Other, "ignored")
    }

    @Test
    fun recordForwardsToSinkWithThreadAndVersion() {
        val received = mutableListOf<EchoErrorDraft>()
        EchoErrorLog.setProcessInfo(EchoErrorProcessInfo("1.2.3 (4)"))
        EchoErrorLog.install { received += it }
        EchoErrorLog.record(EchoErrorSource.Playback, "boom", detail = "decoder")
        assertEquals(1, received.size)
        assertEquals(EchoErrorSource.Playback, received[0].source)
        assertEquals("boom", received[0].summary)
        assertEquals("decoder", received[0].detail)
        assertEquals("1.2.3 (4)", received[0].appVersion)
        assertTrue(received[0].threadName!!.isNotBlank())
    }

    @Test
    fun recordUncaughtKeepsThrowableType() {
        val received = mutableListOf<EchoErrorDraft>()
        EchoErrorLog.install { received += it }
        EchoErrorLog.recordUncaught(IllegalStateException("nope"))
        assertEquals(EchoErrorSource.Crash, received[0].source)
        assertEquals("nope", received[0].summary)
        assertEquals(IllegalStateException::class.java.name, received[0].throwableName)
    }

    @Test
    fun blankSummaryIsIgnored() {
        val received = mutableListOf<EchoErrorDraft>()
        EchoErrorLog.install { received += it }
        EchoErrorLog.record(EchoErrorSource.Other, "  ")
        assertTrue(received.isEmpty())
    }

    @Test
    fun recursiveRecordOnSameThreadIsIgnored() {
        val received = mutableListOf<String>()
        EchoErrorLog.install {
            received += it.summary
            EchoErrorLog.record(EchoErrorSource.Other, "nested")
        }
        EchoErrorLog.record(EchoErrorSource.Crash, "outer")
        assertEquals(listOf("outer"), received)
    }

    @Test
    fun diagnosticTextIncludesContext() {
        val text = EchoErrorRecord(
            id = 1,
            occurredAtEpochMs = 20L,
            source = EchoErrorSource.Usb,
            summary = "open failed",
            detail = "endpoint",
            stackTrace = "stack",
            count = 2,
            threadName = "main",
            appVersion = "0.1 (1)",
            throwableName = "java.io.IOException",
            firstOccurredAtEpochMs = 10L,
        ).toDiagnosticText()
        assertTrue(text.contains("source=Usb"))
        assertTrue(text.contains("count=2"))
        assertTrue(text.contains("thread=main"))
        assertTrue(text.contains("appVersion=0.1 (1)"))
        assertTrue(text.contains("throwable=java.io.IOException"))
        assertTrue(text.contains("firstOccurredAtEpochMs=10"))
    }

    @Test
    fun matchesQueryLooksAtContextFields() {
        val record = EchoErrorRecord(
            id = 1,
            occurredAtEpochMs = 1L,
            source = EchoErrorSource.Network,
            summary = "timeout",
            detail = null,
            stackTrace = null,
            count = 1,
            threadName = "OkHttp",
            appVersion = "9.9",
            throwableName = "java.net.SocketTimeoutException",
        )
        assertTrue(record.matchesQuery("okhttp"))
        assertTrue(record.matchesQuery("9.9"))
        assertTrue(record.matchesQuery("sockettimeout"))
        assertTrue(!record.matchesQuery("playback"))
    }
}
