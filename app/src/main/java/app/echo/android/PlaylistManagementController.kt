package app.echo.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.paging.map
import app.echo.android.data.*
import app.echo.android.feature.library.PlaylistImportFailure
import app.echo.android.feature.library.PlaylistImportUiState
import app.echo.android.model.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets

/** Owns document I/O and import session lifetime; the feature only renders the preview. */
internal class PlaylistManagementController(
    context: Context,
    private val repository: EchoLibraryRepository,
    private val scope: CoroutineScope,
    private val remoteFavorite: (suspend (String, Boolean) -> Boolean)? = null,
) {
    private val resolver = context.applicationContext.contentResolver
    private val mutableImport = MutableStateFlow<PlaylistImportUiState?>(null)
    val importState = mutableImport.asStateFlow()
    private var documentJob: Job? = null
    private val documentIo = Mutex()

    fun order(id: String) = repository.observePlaylistOrder(id)
    fun stats(id: String) = repository.observePlaylistStats(id)
    suspend fun sort(id: String, order: PlaylistTrackOrder) = repository.setPlaylistOrder(id, order)
    fun candidates(query: String) = repository.pagedTracks(query).map { data -> data.map { it.toEchoTrack() } }
    suspend fun add(id: String, tracks: List<String>) = repository.addTracksToPlaylist(id, tracks)
    suspend fun remove(id: String, trackId: String): Boolean {
        if (id == EchoPlaylist.LikedSongsId && trackId.startsWith("subsonic:") && remoteFavorite != null)
            return remoteFavorite.invoke(trackId, false)
        return withContext(Dispatchers.IO) { repository.removeTrackFromLocalPlaylist(id, trackId) }
    }
    suspend fun move(id: String, trackId: String, direction: Int) = repository.movePlaylistTrack(id, trackId, direction)
    suspend fun copy(id: String, name: String) = repository.copyPlaylist(id, name)

    fun setArtwork(id: String, uri: Uri) {
        tryTakePersistableReadPermission(applicationContext, uri)
        scope.launch {
            try { check(repository.setPlaylistArtwork(id, uri.toString())) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableImport.value = PlaylistImportUiState.Failed(PlaylistImportFailure.Cover) }
        }
    }

    private val applicationContext = context.applicationContext

    fun prepare(uri: Uri) {
        documentJob?.cancel()
        documentJob = scope.launch {
            mutableImport.value = PlaylistImportUiState.Reading
            try {
                val (name, text) = read(uri)
                val preview = repository.preparePlaylistImport(text)
                mutableImport.value = PlaylistImportUiState.Preview(name, preview)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ImportFailure) { mutableImport.value = PlaylistImportUiState.Failed(failure.reason) }
            catch (failure: IllegalArgumentException) {
                mutableImport.value = PlaylistImportUiState.Failed(
                    if (failure.message == "Empty playlist") PlaylistImportFailure.Empty else PlaylistImportFailure.TooMany)
            } catch (_: Exception) { mutableImport.value = PlaylistImportUiState.Failed(PlaylistImportFailure.Read) }
        }
    }

    fun confirm(name: String) {
        val preview = mutableImport.value as? PlaylistImportUiState.Preview ?: return
        if (preview.saving || name.isBlank() || preview.result.trackIds.isEmpty()) return
        mutableImport.value = preview.copy(saving = true)
        documentJob = scope.launch {
            try {
                val created = repository.createImportedPlaylist(name, preview.result)
                mutableImport.value = if (created != null) PlaylistImportUiState.Imported(created)
                    else PlaylistImportUiState.Failed(PlaylistImportFailure.Save)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableImport.value = PlaylistImportUiState.Failed(PlaylistImportFailure.Save) }
        }
    }

    fun dismiss() { documentJob?.cancel(); documentJob = null; mutableImport.value = null }

    suspend fun importImmediately(uri: Uri): EchoPlaylist? {
        val (name, text) = read(uri)
        return repository.importM3uPlaylist(name, text)
    }

    fun export(id: String, uri: Uri) {
        documentJob?.cancel()
        documentJob = scope.launch {
            try {
                val text = repository.exportM3uPlaylist(id) ?: throw IllegalStateException("Empty playlist")
                documentIo.withLock { withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("No output stream")
                    output.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
                } }
                mutableImport.value = PlaylistImportUiState.Exported
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableImport.value = PlaylistImportUiState.Failed(PlaylistImportFailure.Export) }
        }
    }

    private suspend fun read(uri: Uri): Pair<String, String> = documentIo.withLock { withContext(Dispatchers.IO) {
        val providerName = if (uri.scheme == "content") resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } else null
        val name = providerName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Playlist"
        val input = resolver.openInputStream(uri) ?: throw ImportFailure(PlaylistImportFailure.Read)
        val bytes = input.use {
            val buffer = ByteArray(PlaylistImportLimits.MaxBytes + 1)
            var size = 0
            while (size < buffer.size) {
                currentCoroutineContext().ensureActive()
                val count = it.read(buffer, size, buffer.size - size)
                if (count < 0) break
                if (count == 0) {
                    val single = it.read()
                    if (single < 0) break
                    buffer[size++] = single.toByte()
                } else size += count
            }
            if (size > PlaylistImportLimits.MaxBytes) throw ImportFailure(PlaylistImportFailure.TooLarge)
            buffer.copyOf(size)
        }
        val text = decodeLegacyTagText(bytes) ?: bytes.toString(StandardCharsets.UTF_8)
        if (text.isBlank()) throw ImportFailure(PlaylistImportFailure.Empty)
        name.substringBeforeLast('.').trim().take(80) to text
    } }

    private class ImportFailure(val reason: PlaylistImportFailure) : IllegalArgumentException()
}
