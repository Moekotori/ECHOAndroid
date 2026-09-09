package app.echo.android.data

import android.content.Context
import app.echo.android.model.error.EchoErrorDraft
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorRecord
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EchoErrorLogRepository internal constructor(
    private val dao: EchoErrorLogDao,
    private val pendingCrashFile: File,
    private val ioScope: CoroutineScope,
) : EchoErrorLog.Sink {
    private val mutex = Mutex()

    val records: Flow<List<EchoErrorRecord>> =
        dao.observeAll().map { rows -> rows.map { it.toRecord() } }

    val count: Flow<Int> =
        dao.observeCount().distinctUntilChanged()

    override fun record(draft: EchoErrorDraft) {
        ioScope.launch {
            persist(draft)
        }
    }

    fun importPendingCrash() {
        ioScope.launch {
            val draft = EchoErrorCrashFile.readAndDelete(pendingCrashFile) ?: return@launch
            persist(draft)
        }
    }

    suspend fun clear() {
        mutex.withLock {
            dao.deleteAll()
        }
    }

    suspend fun delete(id: Long) {
        mutex.withLock {
            dao.deleteById(id)
        }
    }

    internal suspend fun persist(draft: EchoErrorDraft) {
        val summary = EchoErrorLogPolicy.normalizeSummary(draft.summary)
        if (summary.isEmpty()) return
        val throwableMessage = draft.throwable?.message?.trim()?.takeIf { it.isNotEmpty() && it != summary }
        val incoming = EchoErrorDraft(
            source = draft.source,
            summary = summary,
            detail = EchoErrorLogPolicy.normalizeDetail(draft.detail ?: throwableMessage),
            stackTrace = EchoErrorLogPolicy.normalizeStack(
                draft.stackTrace ?: EchoErrorLogPolicy.stackTrace(draft.throwable),
            ),
            occurredAtEpochMs = draft.occurredAtEpochMs,
            threadName = EchoErrorLogPolicy.normalizeThreadName(draft.threadName),
            appVersion = EchoErrorLogPolicy.normalizeAppVersion(draft.appVersion),
            throwableName = EchoErrorLogPolicy.normalizeThrowableName(
                draft.throwableName ?: draft.throwable?.javaClass?.name,
            ),
            firstOccurredAtEpochMs = draft.firstOccurredAtEpochMs,
        )
        mutex.withLock {
            val latest = dao.latest()
            if (latest != null && EchoErrorLogPolicy.shouldMerge(latest.toRecord(), incoming)) {
                dao.update(
                    latest.copy(
                        count = latest.count + 1,
                        occurredAtEpochMs = incoming.occurredAtEpochMs,
                        firstOccurredAtEpochMs = EchoErrorLogPolicy.mergedFirstOccurredAt(
                            latest.toRecord(),
                            incoming,
                        ),
                        detail = incoming.detail ?: latest.detail,
                        stackTrace = incoming.stackTrace ?: latest.stackTrace,
                        threadName = latest.threadName ?: incoming.threadName,
                        appVersion = latest.appVersion ?: incoming.appVersion,
                        throwableName = latest.throwableName ?: incoming.throwableName,
                    ),
                )
                return
            }
            dao.insert(
                EchoErrorLogEntity(
                    occurredAtEpochMs = incoming.occurredAtEpochMs,
                    source = incoming.source.name,
                    summary = incoming.summary,
                    detail = incoming.detail,
                    stackTrace = incoming.stackTrace,
                    count = 1,
                    threadName = incoming.threadName,
                    appVersion = incoming.appVersion,
                    throwableName = incoming.throwableName,
                    firstOccurredAtEpochMs = incoming.firstOccurredAtEpochMs,
                ),
            )
            val overflow = dao.count() - EchoErrorLogPolicy.MaxRecords
            if (overflow > 0) {
                dao.deleteOldest(overflow)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: EchoErrorLogRepository? = null

        fun create(context: Context): EchoErrorLogRepository {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: EchoErrorLogRepository(
                    dao = EchoErrorLogDatabase.create(context).errorLogDao(),
                    pendingCrashFile = EchoErrorCrashFile.pendingFile(context),
                    ioScope = CoroutineScope(
                        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> },
                    ),
                ).also { repository ->
                    instance = repository
                    EchoErrorLog.install(repository)
                    repository.importPendingCrash()
                }
            }
        }
    }
}
