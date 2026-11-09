package app.echo.android.data

import android.content.IntentSender
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class TrackMetadataUpdateResult(
    val indexUpdated: Boolean,
    val fileWrite: EmbeddedTagWriteResult,
)

sealed class EmbeddedTagWriteResult {
    data class Written(
        val sizeBytes: Long,
        val dateModifiedSeconds: Long,
    ) : EmbeddedTagWriteResult()
    data object NotLocal : EmbeddedTagWriteResult()
    data object UnsupportedFormat : EmbeddedTagWriteResult()
    data class NeedsMediaStoreConsent(
        val uriString: String,
        val intentSender: IntentSender? = null,
    ) : EmbeddedTagWriteResult()
    data object NeedsStoragePermission : EmbeddedTagWriteResult()
    data object Failed : EmbeddedTagWriteResult()
}

/** Keep a completed file rewrite and its index update together across cancellation. */
internal suspend fun writeTagsThenUpdateIndex(
    writeFile: suspend () -> EmbeddedTagWriteResult,
    updateIndex: suspend (EmbeddedTagWriteResult) -> Boolean,
): TrackMetadataUpdateResult {
    currentCoroutineContext().ensureActive()
    return withContext(NonCancellable) {
        val result = writeFile()
        val canUpdateIndex = result is EmbeddedTagWriteResult.Written || result == EmbeddedTagWriteResult.NotLocal
        TrackMetadataUpdateResult(canUpdateIndex && updateIndex(result), result)
    }
}
