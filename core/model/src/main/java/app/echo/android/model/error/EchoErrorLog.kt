package app.echo.android.model.error

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide error reporter. Modules call [record]; [app] installs a persistent sink.
 * The sink must not call back into [record] on the same thread.
 */
object EchoErrorLog {
    fun interface Sink {
        fun record(draft: EchoErrorDraft)
    }

    private val sink = AtomicReference<Sink?>(null)
    private val processInfo = AtomicReference<EchoErrorProcessInfo?>(null)
    private val recording = ThreadLocal.withInitial { false }

    fun install(sink: Sink?) {
        this.sink.set(sink)
    }

    fun setProcessInfo(info: EchoErrorProcessInfo?) {
        processInfo.set(info)
    }

    fun recordUncaught(error: Throwable, source: EchoErrorSource = EchoErrorSource.Crash) {
        record(
            source = source,
            summary = error.message?.trim()?.takeIf { it.isNotEmpty() }
                ?: error.javaClass.simpleName.ifBlank { error.javaClass.name },
            throwable = error,
        )
    }

    fun record(
        source: EchoErrorSource,
        summary: String,
        detail: String? = null,
        throwable: Throwable? = null,
        occurredAtEpochMs: Long = System.currentTimeMillis(),
    ) {
        record(
            EchoErrorDraft(
                source = source,
                summary = summary,
                detail = detail,
                occurredAtEpochMs = occurredAtEpochMs,
                throwable = throwable,
            ),
        )
    }

    fun record(draft: EchoErrorDraft) {
        if (draft.summary.isBlank()) return
        if (recording.get() == true) return
        val target = sink.get() ?: return
        val stamped = stamp(draft)
        recording.set(true)
        try {
            target.record(stamped)
        } catch (_: Throwable) {
        } finally {
            recording.set(false)
        }
    }

    internal fun stamp(draft: EchoErrorDraft): EchoErrorDraft {
        val info = processInfo.get()
        return draft.copy(
            threadName = draft.threadName?.trim()?.takeIf { it.isNotEmpty() }
                ?: Thread.currentThread().name,
            appVersion = draft.appVersion?.trim()?.takeIf { it.isNotEmpty() }
                ?: info?.appVersion,
            throwableName = draft.throwableName?.trim()?.takeIf { it.isNotEmpty() }
                ?: draft.throwable?.javaClass?.name,
        )
    }
}
