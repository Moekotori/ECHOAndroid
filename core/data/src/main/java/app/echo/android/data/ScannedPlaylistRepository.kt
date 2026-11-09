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
import kotlin.coroutines.coroutineContext

/** Discovery only runs on demand or after a completed scan. No background watcher. */
class ScannedPlaylistRepository(private val repository: EchoLibraryRepository) {
    private val resolver = repository.appContext.contentResolver
    private val mutex = Mutex()
    private fun id(uri: String, sourcePlaylistKey: String? = null): String {
        val parsed = Uri.parse(uri)
        // Overlapping tree grants can produce different URLs for the same document.
        val identity = "${parsed.authority}|${DocumentsContract.getDocumentId(parsed)}"
        return "local:0:scan-" + digest((identity + (sourcePlaylistKey?.let { "\u0000$it" } ?: "")).toByteArray())
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    suspend fun discover(
        trees: List<String>,
        options: LibraryScanOptions = LibraryScanOptions(excludeNonMusicFolders = false, excludeHiddenFolders = false),
        importITunes: Boolean = true,
        importOtherPlaylists: Boolean = true,
        onlyNew: Boolean = false,
    ): EchoPlaylistDiscoveryResult = withContext(Dispatchers.IO) {
        data class Directory(val tree: Uri, val id: String, val path: String)
        val pending = ArrayDeque<Directory>()
        trees.distinct().take(16).forEach { raw ->
            val tree = Uri.parse(raw)
            val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return@forEach
            val path = MediaStoreAudioFolder.fromTreeUri(tree)?.relativePathPrefix.orEmpty()
            pending.add(Directory(tree, id, path))
        }
        val visited = HashSet<String>()
        val files = LinkedHashMap<String, String>()
        var visitedItems = 0
        var unavailable = 0
        var truncated = false
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            if (visited.size >= 256 || visitedItems >= 8192 || files.size >= 16) { truncated = true; break }
            val (tree, parent, path) = pending.removeFirst()
            if (!options.includesDirectory(path)) continue
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
                            val childPath = path.trimEnd('/') + "/" + name
                            if (options.includesDirectory(childPath)) {
                                if (pending.size < 256) pending.add(Directory(tree, document, childPath)) else truncated = true
                            }
                        } else if (options.explicitRejection(path, name) == null &&
                            when (name.substringAfterLast('.', "").lowercase()) {
                                "xml" -> importITunes
                                in PlaylistFileCodec.DiscoverableExtensions -> importOtherPlaylists
                                else -> false
                            }
                        ) {
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
        val seenIds = HashSet<String>()
        fileLoop@ for ((uri, name) in files) {
            coroutineContext.ensureActive()
            try {
                val itunes = name.endsWith(".xml", ignoreCase = true)
                val dao = repository.database.playlistDao()
                val textPlaylist = if (itunes) null else dao.getPlaylist(id(uri))
                if (onlyNew && textPlaylist != null) continue
                val bytes = read(uri, itunes)
                bytesRead += bytes.size
                if (bytesRead > MaxDiscoveryBytes) { truncated = true; break }
                val text = decodeLegacyTagText(bytes) ?: error("Cannot decode playlist")
                val playlists = if (itunes) {
                    if (!text.contains("<plist") || !text.contains("Playlists")) continue
                    ITunesPlaylistCodec.parse(text)
                } else {
                    val parent = DocumentsContract.getDocumentId(Uri.parse(uri)).substringAfter(':').substringBeforeLast('/', "")
                    val entries = PlaylistFileCodec.parse(text, name).map {
                        if (it.location.isBlank()) it else it.copy(location = resolveScannedPlaylistLocation(it.location, parent))
                    }
                    require(entries.isNotEmpty())
                    listOf(ITunesPlaylist("", name.substringBeforeLast('.'), entries))
                }
                val fingerprint = digest(bytes)
                for (playlist in playlists) {
                    val key = playlist.key.takeIf { itunes }
                    if (!seenIds.add(id(uri, key))) continue
                    val existing = if (itunes) dao.getPlaylist(id(uri, key)) else textPlaylist
                    if (onlyNew && existing != null) continue
                    entriesRead += playlist.entries.size
                    if (entriesRead > 4000 || matching.size >= 32) { truncated = true; break@fileLoop }
                    val candidate = EchoDiscoveredPlaylist(uri, playlist.name, fingerprint, null,
                        alreadyImported = existing != null, existingUpdatedAt = existing?.updatedAtEpochMs,
                        sourcePlaylistKey = key)
                    val entries = if (onlyNew) playlist.entries.map { it.copy(title = null) } else playlist.entries
                    matching += candidate to PlaylistImportMatcher(entries)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { results += EchoDiscoveredPlaylist(uri, name, "", null, error = true) }
        }
        var after = ""
        while (matching.isNotEmpty()) {
            coroutineContext.ensureActive()
            val batch = repository.database.trackDao().playlistMatchBatch(after, localOnly = onlyNew)
            val hidden = if (batch.isEmpty()) emptySet() else repository.database.hiddenTrackDao().hiddenIds(batch.map { it.id }).toSet()
            val visible = batch.filterNot { it.id in hidden }
            val eligible = if (onlyNew) visible.filter {
                options.explicitRejection(it.relativePath, it.fileName) == null
            } else visible
            matching.forEach { it.second.add(eligible) }
            if (batch.size < 512) break
            after = batch.last().id
        }
        results += matching.map { (candidate, matcher) -> candidate.copy(preview = matcher.preview()) }
        EchoPlaylistDiscoveryResult(results, truncated, unavailable)
    }

    /** Imports new matches only. A later rescan leaves existing playlists and their edits intact. */
    suspend fun importNew(
        treeUri: String,
        options: LibraryScanOptions,
        rules: LibraryFolderScanRules,
    ): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        val found = discover(listOf(treeUri), options, rules.importITunes, rules.importOtherPlaylists, onlyNew = true)
        var count = 0
        var partial = found.truncated || found.unavailableDirectories > 0
        for ((uri, candidates) in found.items.groupBy { it.uri }) {
            val matches = candidates.filter { candidate ->
                val preview = candidate.preview
                if (candidate.error || preview == null || preview.unmatchedEntries > 0 || preview.ambiguousEntries > 0) {
                    partial = true
                }
                !preview?.trackIds.isNullOrEmpty()
            }
            if (matches.isEmpty()) continue
            val fingerprint = try {
                digest(read(uri, matches.first().sourcePlaylistKey != null))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                partial = true
                continue
            }
            for (candidate in matches) {
                try {
                    mutex.withLock { writeImport(candidate, fingerprint) }
                    count++
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    partial = true
                }
            }
        }
        count to partial
    }

    suspend fun import(candidate: EchoDiscoveredPlaylist) = withContext(Dispatchers.IO) { mutex.withLock {
        writeImport(candidate, digest(read(candidate.uri, candidate.sourcePlaylistKey != null)))
    } }

    private suspend fun writeImport(candidate: EchoDiscoveredPlaylist, fingerprint: String) {
        val preview = requireNotNull(candidate.preview)
        require(!candidate.error && preview.trackIds.isNotEmpty())
        require(fingerprint == candidate.fingerprint) { "Playlist changed; discover again" }
        repository.database.withTransaction {
            val dao = repository.database.playlistDao()
            val playlistId = id(candidate.uri, candidate.sourcePlaylistKey)
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
    }

    private suspend fun read(uri: String, itunes: Boolean): ByteArray = requireNotNull(resolver.openInputStream(Uri.parse(uri))).use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val limit = if (itunes) MaxITunesBytes else PlaylistImportLimits.MaxBytes
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit)
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private companion object {
        const val MaxITunesBytes = 8 * 1024 * 1024
        const val MaxDiscoveryBytes = 12 * 1024 * 1024
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
