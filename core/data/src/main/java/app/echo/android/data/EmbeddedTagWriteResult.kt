package app.echo.android.data

import android.content.IntentSender

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
