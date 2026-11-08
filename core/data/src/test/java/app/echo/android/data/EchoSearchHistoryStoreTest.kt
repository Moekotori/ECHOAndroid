package app.echo.android.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import okio.Path.Companion.toOkioPath
import okio.FileSystem

class EchoSearchHistoryStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val jobs = mutableListOf<Job>()

    private fun store(file: File): EchoSearchHistoryStore {
        val job = SupervisorJob().also(jobs::add)
        // Okio supports replacing an existing file on the Windows JVM test host.
        return EchoSearchHistoryStore(PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { file.toOkioPath() }),
            scope = CoroutineScope(job + Dispatchers.IO),
        ))
    }

    @After fun closeStores() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
    }

    @Test fun persistsTermsAndMovesRepeatedSearchToTheFront() = runBlocking {
        val file = File(temporary.root, "history.preferences_pb")
        val history = store(file)
        history.record("  初音ミク \"Live\" ")
        history.record("Radiohead")
        history.record(" other ")
        history.record(" radioHEAD ")
        val expected = listOf("radioHEAD", "other", "初音ミク \"Live\"")
        assertEquals(expected, history.queries.first())
        jobs.last().cancelAndJoin()
        assertEquals(expected, store(file).queries.first())
    }

    @Test fun keepsOnlyTwentyRecentTermsAndIgnoresEmptyOrOversizedInput() = runBlocking {
        val history = store(File(temporary.root, "bounded.preferences_pb"))
        repeat(25) { history.record("song $it") }
        history.record(" \n ")
        history.record("x".repeat(201))
        assertEquals((24 downTo 5).map { "song $it" }, history.queries.first())
    }

    @Test fun deleteAndClearStayDeletedAfterReopening() = runBlocking {
        val file = File(temporary.root, "deleted.preferences_pb")
        val history = store(file)
        history.record("one")
        history.record("two")
        history.remove(" ONE ")
        assertEquals(listOf("two"), history.queries.first())
        history.clear()
        jobs.last().cancelAndJoin()
        assertEquals(emptyList<String>(), store(file).queries.first())
    }
}
