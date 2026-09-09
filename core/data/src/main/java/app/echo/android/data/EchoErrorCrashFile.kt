package app.echo.android.data

import android.content.Context
import app.echo.android.model.error.EchoErrorDraft
import app.echo.android.model.error.EchoErrorSource
import java.io.File
import org.json.JSONObject

object EchoErrorCrashFile {
    fun pendingFile(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, EchoErrorLogPolicy.PendingFileName)

    fun write(
        file: File,
        throwable: Throwable,
        threadName: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
        appVersion: String? = null,
    ) {
        val summary = throwable.message?.trim()?.takeIf { it.isNotEmpty() }
            ?: throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
        writeDraft(
            file = file,
            draft = EchoErrorDraft(
                source = EchoErrorSource.Crash,
                summary = summary,
                stackTrace = EchoErrorLogPolicy.stackTrace(throwable),
                occurredAtEpochMs = nowEpochMs,
                throwable = throwable,
                threadName = threadName,
                appVersion = appVersion,
                throwableName = throwable.javaClass.name,
                firstOccurredAtEpochMs = nowEpochMs,
            ),
        )
    }

    fun writeDraft(file: File, draft: EchoErrorDraft) {
        val json = JSONObject()
        json.put("source", draft.source.name)
        json.put("summary", EchoErrorLogPolicy.normalizeSummary(draft.summary))
        EchoErrorLogPolicy.normalizeDetail(draft.detail)?.let { json.put("detail", it) }
        EchoErrorLogPolicy.normalizeStack(draft.stackTrace)?.let { json.put("stackTrace", it) }
        json.put("occurredAtEpochMs", draft.occurredAtEpochMs)
        json.put("firstOccurredAtEpochMs", draft.firstOccurredAtEpochMs)
        EchoErrorLogPolicy.normalizeThreadName(draft.threadName)?.let { json.put("threadName", it) }
        EchoErrorLogPolicy.normalizeAppVersion(draft.appVersion)?.let { json.put("appVersion", it) }
        EchoErrorLogPolicy.normalizeThrowableName(
            draft.throwableName ?: draft.throwable?.javaClass?.name,
        )?.let { json.put("throwableName", it) }
        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }

    fun readAndDelete(file: File): EchoErrorDraft? {
        if (!file.isFile) return null
        val raw = runCatching { file.readText() }.getOrNull()
        runCatching { file.delete() }
        return decode(raw)
    }

    fun decode(raw: String?): EchoErrorDraft? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val summary = EchoErrorLogPolicy.normalizeSummary(json.optString("summary"))
            if (summary.isEmpty()) return@runCatching null
            val occurredAt = json.optLong("occurredAtEpochMs").takeIf { it > 0L }
                ?: System.currentTimeMillis()
            EchoErrorDraft(
                source = EchoErrorSource.fromId(json.optString("source")),
                summary = summary,
                detail = EchoErrorLogPolicy.normalizeDetail(json.optString("detail").takeIf { it.isNotBlank() }),
                stackTrace = EchoErrorLogPolicy.normalizeStack(json.optString("stackTrace").takeIf { it.isNotBlank() }),
                occurredAtEpochMs = occurredAt,
                threadName = EchoErrorLogPolicy.normalizeThreadName(json.optString("threadName").takeIf { it.isNotBlank() }),
                appVersion = EchoErrorLogPolicy.normalizeAppVersion(json.optString("appVersion").takeIf { it.isNotBlank() }),
                throwableName = EchoErrorLogPolicy.normalizeThrowableName(json.optString("throwableName").takeIf { it.isNotBlank() }),
                firstOccurredAtEpochMs = json.optLong("firstOccurredAtEpochMs").takeIf { it > 0L } ?: occurredAt,
            )
        }.getOrNull()
    }
}
