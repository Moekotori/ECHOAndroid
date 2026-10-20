package app.echo.android

import app.echo.android.data.EmbeddedTagWriteResult
import app.echo.android.data.TrackMetadataUpdateResult

/** A batch item waits for permission before proceeding; each permission kind is requested once. */
internal suspend fun writeTagsWithAuthorization(
    write: suspend () -> TrackMetadataUpdateResult,
    authorize: suspend (EmbeddedTagWriteResult) -> Boolean,
): TrackMetadataUpdateResult {
    var result = write()
    var mediaRequested = false
    var storageRequested = false
    repeat(2) {
        when (result.fileWrite) {
            is EmbeddedTagWriteResult.NeedsMediaStoreConsent -> {
                if (mediaRequested) return result
                mediaRequested = true
            }
            EmbeddedTagWriteResult.NeedsStoragePermission -> {
                if (storageRequested) return result
                storageRequested = true
            }
            else -> return result
        }
        if (!authorize(result.fileWrite)) return result
        result = write()
    }
    return result
}
