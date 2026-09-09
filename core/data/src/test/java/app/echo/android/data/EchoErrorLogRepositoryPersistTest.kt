package app.echo.android.data

import app.echo.android.model.error.EchoErrorDraft
import app.echo.android.model.error.EchoErrorSource
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoErrorLogRepositoryPersistTest {
    @Test
    fun mergeIncrementsCountInsideWindow() = runBlocking {
        val dao = FakeEchoErrorLogDao()
        val repository = EchoErrorLogRepository(
            dao = dao,
            pendingCrashFile = File.createTempFile("echo-error", ".json"),
            ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        repository.persist(
            EchoErrorDraft(EchoErrorSource.Playback, "boom", occurredAtEpochMs = 1_000L),
        )
        repository.persist(
            EchoErrorDraft(EchoErrorSource.Playback, "boom", occurredAtEpochMs = 1_500L),
        )
        assertEquals(1, dao.rows.size)
        assertEquals(2, dao.rows[0].count)
        assertEquals(1_500L, dao.rows[0].occurredAtEpochMs)
        assertEquals(1_000L, dao.rows[0].firstOccurredAtEpochMs)
    }

    @Test
    fun deleteRemovesOneRecord() = runBlocking {
        val dao = FakeEchoErrorLogDao()
        val repository = EchoErrorLogRepository(
            dao = dao,
            pendingCrashFile = File.createTempFile("echo-error", ".json"),
            ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        repository.persist(EchoErrorDraft(EchoErrorSource.Other, "keep", occurredAtEpochMs = 1L))
        repository.persist(EchoErrorDraft(EchoErrorSource.Other, "drop", occurredAtEpochMs = 2L))
        val dropId = dao.rows.first { it.summary == "drop" }.id
        repository.delete(dropId)
        assertEquals(listOf("keep"), dao.rows.map { it.summary })
    }

    @Test
    fun pruneKeepsNewestRecords() = runBlocking {
        val dao = FakeEchoErrorLogDao()
        val repository = EchoErrorLogRepository(
            dao = dao,
            pendingCrashFile = File.createTempFile("echo-error", ".json"),
            ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        repeat(EchoErrorLogPolicy.MaxRecords + 3) { index ->
            repository.persist(
                EchoErrorDraft(
                    source = EchoErrorSource.Other,
                    summary = "error-$index",
                    occurredAtEpochMs = index.toLong(),
                ),
            )
        }
        assertEquals(EchoErrorLogPolicy.MaxRecords, dao.rows.size)
        assertEquals("error-3", dao.rows.minBy { it.occurredAtEpochMs }.summary)
        assertEquals(
            "error-${EchoErrorLogPolicy.MaxRecords + 2}",
            dao.rows.maxBy { it.occurredAtEpochMs }.summary,
        )
    }
}

private class FakeEchoErrorLogDao : EchoErrorLogDao {
    val rows = mutableListOf<EchoErrorLogEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<EchoErrorLogEntity>> = MutableStateFlow(rows)

    override fun observeCount(): Flow<Int> = MutableStateFlow(rows.size)

    override suspend fun latest(): EchoErrorLogEntity? =
        rows.maxWithOrNull(compareBy<EchoErrorLogEntity> { it.occurredAtEpochMs }.thenBy { it.id })

    override suspend fun count(): Int = rows.size

    override suspend fun insert(entity: EchoErrorLogEntity): Long {
        val stored = entity.copy(id = nextId++)
        rows += stored
        return stored.id
    }

    override suspend fun update(entity: EchoErrorLogEntity) {
        val index = rows.indexOfFirst { it.id == entity.id }
        if (index >= 0) rows[index] = entity
    }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun deleteOldest(overflow: Int) {
        if (overflow <= 0) return
        val doomed = rows.sortedWith(compareBy<EchoErrorLogEntity> { it.occurredAtEpochMs }.thenBy { it.id })
            .take(overflow)
            .map { it.id }
            .toSet()
        rows.removeAll { it.id in doomed }
    }
}
