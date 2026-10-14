package app.echo.android.data

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import androidx.annotation.RequiresApi
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

@RequiresApi(30)
private fun mediaStoreDeleteRequest(resolver: ContentResolver, uris: List<Uri>) =
    MediaStore.createDeleteRequest(resolver, uris)

data class LibraryTrackDeletionResult(val removedIds: Set<String>, val cancelled: Boolean = false)

object LibraryTrackDeletionPolicy {
    const val Limit = 200

    fun canDeleteOriginal(track: EchoTrack): Boolean = track.source.isLocalAudioFile &&
        !CueSheetPolicy.isCueTrackId(track.id) && !track.uri.contains('#') &&
        track.clipStartMs == 0L && track.clipEndMs == 0L &&
        track.uri.substringBefore(':') in setOf("content", "file")

    fun canDeleteOriginals(tracks: List<EchoTrack>): Boolean = tracks.isNotEmpty() &&
        tracks.size <= Limit && tracks.all(::canDeleteOriginal)
}

/** File operations are sequential and bounded. Consent is supplied by the Activity owner. */
suspend fun EchoLibraryRepository.deleteSelectedTracks(
    tracks: List<EchoTrack>,
    deleteOriginals: Boolean,
    authorize: suspend (IntentSender) -> Boolean,
    onOriginalFilesDeleted: suspend (Set<String>) -> Unit = {},
): LibraryTrackDeletionResult = withContext(Dispatchers.IO) {
    require(tracks.size <= LibraryTrackDeletionPolicy.Limit)
    val requested = tracks.distinctBy(EchoTrack::id)
    val ids = requested.map(EchoTrack::id)
    val dao = database.trackDao()
    if (!deleteOriginals) {
        dao.deleteScanBatch(ids)
        return@withContext LibraryTrackDeletionResult(ids.toSet())
    }
    require(LibraryTrackDeletionPolicy.canDeleteOriginals(requested))
    // Never delete an old URI after the selected row has been relinked or edited elsewhere.
    val rows = dao.getTracksByIds(ids).associateBy { it.id }
    val groups = requested.filter { track ->
        val row = rows[track.id]
        row != null && row.contentUri == track.uri && row.source == track.source.id
    }.groupBy { CueSheetPolicy.playbackUri(it.uri) }
    val resolver = appContext.contentResolver
    val successfulUris = LinkedHashSet<String>()
    val removedIds = LinkedHashSet<String>()
    var cancelled = false
    try {
        if (Build.VERSION.SDK_INT >= 30) {
            val mediaUris = groups.keys.map(Uri::parse)
                .filter { it.scheme == "content" && it.authority == MediaStore.AUTHORITY }
            if (mediaUris.isNotEmpty()) {
                val approved = authorize(mediaStoreDeleteRequest(resolver, mediaUris).intentSender)
                if (!approved) return@withContext LibraryTrackDeletionResult(emptySet(), cancelled = true)
                // Android 11+ performs deletion itself. Verify and reconcile even if the page exits now.
                withContext(NonCancellable) {
                    mediaUris.forEach { uri ->
                        try {
                            val absent = resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                                ?.use { !it.moveToFirst() } == true
                            if (absent) successfulUris.add(uri.toString())
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { /* Unknown outcomes retain the index row. */ }
                    }
                }
            }
        }
        for (value in groups.keys) {
            coroutineContext.ensureActive()
            val uri = Uri.parse(value)
            if (Build.VERSION.SDK_INT >= 30 && uri.authority == MediaStore.AUTHORITY) continue
            try {
                val deleted = when {
                    uri.scheme == "file" -> {
                        val file = File(uri.path.orEmpty())
                        uri.authority.isNullOrEmpty() && file.isAbsolute && (!file.exists() || (file.isFile && file.delete()))
                    }
                    DocumentsContract.isDocumentUri(appContext, uri) -> DocumentsContract.deleteDocument(resolver, uri)
                    uri.scheme == "content" -> resolver.delete(uri, null, null) > 0
                    else -> false
                }
                if (deleted) successfulUris.add(value)
            } catch (error: SecurityException) {
                if (Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException) {
                    if (!authorize(error.userAction.actionIntent.intentSender)) {
                        cancelled = true
                        break
                    }
                    try {
                        if (resolver.delete(uri, null, null) > 0) successfulUris.add(value)
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* Keep this row selected for retry. */ }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* One failed provider must not remove its database row. */ }
        }
    } finally {
        finalizeDeletedFiles(successfulUris, onOriginalFilesDeleted) {
                val relatedIds = dao.trackIdsForDeletion(deletedSourceQuery(successfulUris))
                relatedIds.chunked(200).forEach { dao.deleteScanBatch(it) }
                requested.filter { CueSheetPolicy.playbackUri(it.uri) in successfulUris }
                    .forEach { removedIds.add(it.id) }
        }
    }
    LibraryTrackDeletionResult(removedIds, cancelled)
}

/** Physical deletion has already happened: reconcile it despite cancellation or an index failure. */
internal suspend fun finalizeDeletedFiles(
    uris: Set<String>,
    notifyPlayback: suspend (Set<String>) -> Unit,
    cleanupIndex: suspend () -> Unit,
) {
    if (uris.isEmpty()) return
    withContext(NonCancellable) {
        try { cleanupIndex() }
        finally { notifyPlayback(uris.toSet()) }
    }
}

/** Match exact files and their CUE fragments using the existing contentUri index. */
private fun deletedSourceQuery(uris: Set<String>): SimpleSQLiteQuery {
    val terms = uris.map { "(contentUri = ? OR (contentUri >= ? AND contentUri < ?))" }
    val args = uris.flatMap { listOf(it, "$it#${CueSheetPolicy.CueUriFragment}=", "$it#${CueSheetPolicy.CueUriFragment}>") }
    return SimpleSQLiteQuery(
        "SELECT id FROM library_tracks WHERE source IN ('mediastore', 'saf') AND (${terms.joinToString(" OR ")})",
        args.toTypedArray(),
    )
}
