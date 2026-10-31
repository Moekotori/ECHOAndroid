package app.echo.android.data

import android.net.Uri
import android.provider.DocumentsContract
import app.echo.android.model.library.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** Discovery only runs on demand or after a completed scan. No background watcher. */
class ScannedPlaylistRepository(private val repository: EchoLibraryRepository) {
    private val resolver = repository.appContext.contentResolver
    private val mutex = Mutex()
    private fun id(uri: String): String {
        val parsed = Uri.parse(uri)
        // Overlapping tree grants can produce different URLs for the same document.
        val identity = "${parsed.authority}|${DocumentsContract.getDocumentId(parsed)}"
        return "local:0:scan-" + digest(identity.toByteArray())
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    suspend fun discover(trees: List<String>): EchoPlaylistDiscoveryResult = withContext(Dispatchers.IO) {
        val pending = ArrayDeque<Pair<Uri, String>>()
        trees.distinct().take(16).forEach { raw ->
            val tree = Uri.parse(raw)
            runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()?.let { pending.add(tree to it) }
        }
        val visited = HashSet<String>()
        val files = LinkedHashMap<String, String>()
        var visitedItems = 0
        var unavailable = 0
        var truncated = false
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            if (visited.size >= 256 || visitedItems >= 8192 || files.size >= 16) { truncated = true; break }
            val (tree, parent) = pending.removeFirst()
            if (!visited.add("${tree.authority}|$parent")) continue
            try {
                resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent), arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        if (++visitedItems > 8192 || files.size >= 16) { truncated = true; break }
                        val name = cursor.getString(1) ?: continue
                        val document = cursor.getString(0) ?: continue
                        if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (pending.size < 256) pending.add(tree to document) else truncated = true
                        } else if (name.substringAfterLast('.').lowercase() in listOf("m3u", "m3u8")) {
                            files[DocumentsContract.buildDocumentUriUsingTree(tree, document).toString()] = name
                        }
                    }
                } ?: run { unavailable++ }
            } catch (e: SecurityException) { unavailable++ }
            catch (e: java.io.IOException) { unavailable++ }
        }
        val results = ArrayList<EchoDiscoveredPlaylist>()
        val matching = ArrayList<Pair<EchoDiscoveredPlaylist, PlaylistImportMatcher>>()
        var bytesRead = 0
        var entriesRead = 0
        for ((uri, name) in files) {
            coroutineContext.ensureActive()
            try {
                val bytes = read(uri)
                bytesRead += bytes.size
                if (bytesRead > 2 * 1024 * 1024) { truncated = true; break }
                val text = decodeLegacyTagText(bytes) ?: error("Cannot decode playlist")
                val parent = DocumentsContract.getDocumentId(Uri.parse(uri)).substringAfter(':').substringBeforeLast('/', "")
                val entries = PlaylistFileCodec.parse(text).map { it.copy(location = resolveScannedPlaylistLocation(it.location, parent)) }
                entriesRead += entries.size
                require(entries.isNotEmpty())
                if (entriesRead > 4000) { truncated = true; break }
                val existing = repository.database.playlistDao().getPlaylist(id(uri))
                val candidate = EchoDiscoveredPlaylist(uri, name.substringBeforeLast('.'), digest(bytes), null,
                    alreadyImported = existing != null, existingUpdatedAt = existing?.updatedAtEpochMs)
                matching += candidate to PlaylistImportMatcher(entries)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { results += EchoDiscoveredPlaylist(uri, name, "", null, error = true) }
        }
        var after = ""
        while (matching.isNotEmpty()) {
            coroutineContext.ensureActive()
            val batch = repository.database.trackDao().playlistMatchBatch(after)
            val hidden = if (batch.isEmpty()) emptySet() else repository.database.hiddenTrackDao().hiddenIds(batch.map { it.id }).toSet()
            val visible = batch.filterNot { it.id in hidden }
            matching.forEach { it.second.add(visible) }
            if (batch.size < 512) break
            after = batch.last().id
        }
        results += matching.map { (candidate, matcher) -> candidate.copy(preview = matcher.preview()) }
        EchoPlaylistDiscoveryResult(results, truncated, unavailable)
    }

    suspend fun import(candidate: EchoDiscoveredPlaylist) = withContext(Dispatchers.IO) { mutex.withLock {
        val preview = requireNotNull(candidate.preview)
        require(!candidate.error && preview.trackIds.isNotEmpty())
        require(digest(read(candidate.uri)) == candidate.fingerprint) { "Playlist changed; discover again" }
        repository.database.withTransaction {
            val dao = repository.database.playlistDao()
            val playlistId = id(candidate.uri)
            val existing = dao.getPlaylist(playlistId)
            require(existing?.updatedAtEpochMs == candidate.existingUpdatedAt) { "Playlist was edited; discover again" }
            val valid = preview.trackIds.chunked(400).flatMap { repository.database.trackDao().getTracksByIds(it) }.map { it.id }.toSet()
            require(valid.size == preview.trackIds.size) { "Library changed; discover again" }
            require(preview.trackIds.chunked(400).all { repository.database.hiddenTrackDao().hiddenIds(it).isEmpty() })
            val ids = preview.trackIds
            require(ids.isNotEmpty())
            val now = System.currentTimeMillis()
            dao.replacePlaylist(LibraryPlaylistEntity(playlistId, existing?.name ?: candidate.name.take(80), "mediastore",
                existing?.artworkUri, ids.size, now), ids.mapIndexed { index, track -> LibraryPlaylistTrackEntity(playlistId, track, index) })
            dao.refreshMembershipSummary(playlistId, now)
        }
    } }

    private fun read(uri: String): ByteArray = requireNotNull(resolver.openInputStream(Uri.parse(uri))).use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= PlaylistImportLimits.MaxBytes)
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

internal fun resolveScannedPlaylistLocation(location: String, parent: String): String {
    val raw = location.replace('\\', '/')
    if (raw.startsWith('/') || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(raw)) return location
    val parts = ArrayList<String>()
    for (part in "$parent/$raw".split('/')) when (part) {
        "", "." -> Unit
        ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
        else -> parts += part
    }
    return parts.joinToString("/")
}
