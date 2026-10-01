package app.echo.android

import android.content.Context
import android.net.Uri
import app.echo.android.data.*
import app.echo.android.model.library.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Session wiring for library tools; Room and file operations remain in core:data. */
internal class LibraryExperienceController(private val repository: EchoLibraryRepository, private val context: Context) {
    suspend fun preview(rule: EchoSmartPlaylistRule) = repository.previewSmartPlaylist(rule)
    suspend fun rule(id: String) = repository.smartPlaylistRule(id)
    suspend fun save(id: String?, name: String, rule: EchoSmartPlaylistRule) = repository.saveSmartPlaylist(id, name, rule)
    suspend fun pin(id: String, pinned: Boolean) = repository.pinSmartPlaylistToHome(id, pinned)
    suspend fun inspect() = repository.inspectLibraryFiles()
    suspend fun candidates(track: EchoTrack) = repository.repairCandidates(track)
    suspend fun relink(id: String, track: EchoTrack) = repository.relinkLibraryTrack(id, track)
    suspend fun readFile(uri: Uri): EchoTrack? = withContext(Dispatchers.IO) {
        tryTakePersistableReadPermission(context, uri)
        resolveIncomingAudioTrack(context, repository, uri)
    }
    fun bookmarks(id: String) = repository.observeBookmarks(id)
    suspend fun saveBookmark(id: String, position: Long, label: String) = repository.saveBookmark(id, position, label)
    suspend fun saveBookmark(track: EchoTrack, position: Long, label: String) = repository.saveBookmark(track, position, label)
    fun moments(query: String) = repository.pagedMoments(query)
    suspend fun searchMoments(query: String) = repository.searchMoments(query)
    suspend fun favorites(ids: List<String>, selected: Boolean) = repository.batchFavorites(ids, selected)
    suspend fun addToPlaylist(id: String, tracks: List<String>) = repository.batchAddToPlaylist(id, tracks)
    suspend fun tags(ids: List<String>, patch: EchoBatchTagPatch, progress: (Int) -> Unit) = repository.batchTags(ids, patch, progress)
    suspend fun deleteBookmark(id: String) = repository.deleteBookmark(id)
    suspend fun exportBookmarks() = repository.exportBackupBookmarks()
    suspend fun restoreBookmarks(bookmarks: List<app.echo.android.model.backup.EchoBackupBookmark>) = repository.restoreBackupBookmarks(bookmarks)
}
