package app.echo.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EchoSettingsSnapshotFlowTest {
    @Test fun concurrentReadersDecodeOnceOffTheCollectingThread() = runBlocking {
        val source = MutableStateFlow(1)
        val caller = Thread.currentThread()
        var decodes = 0
        val settings = source.mapSettingsSnapshot(revision = { Unit }) {
            assertNotEquals(caller, Thread.currentThread())
            decodes++
            "settings-$it"
        }
        assertEquals(List(4) { "settings-1" }, List(4) { async(Dispatchers.Default) { settings.first() } }.awaitAll())
        assertEquals(1, decodes)
        source.value = 2
        assertEquals("settings-2", settings.first())
        assertEquals(2, decodes)
    }

    @Test fun externalRevisionInvalidatesAnOtherwiseIdenticalSnapshot() = runBlocking {
        val source = MutableStateFlow(1)
        var revision = 0
        var decodes = 0
        val settings = source.mapSettingsSnapshot(revision = { revision }) { decodes++; revision }
        assertEquals(0, settings.first())
        revision++
        assertEquals(1, settings.first())
        assertEquals(1, settings.first())
        assertEquals(2, decodes)
    }

    @Test fun aRevisionChangedByMigrationIsNotCachedUnderTheOldRevision() = runBlocking {
        var revision = 0
        var decodes = 0
        val settings = MutableStateFlow(1).mapSettingsSnapshot(revision = { revision }) {
            decodes++
            val before = revision
            revision = 1
            before
        }
        assertEquals(0, settings.first())
        assertEquals(1, settings.first())
        assertEquals(1, settings.first())
        assertEquals(2, decodes)
    }
}
