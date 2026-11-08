package app.echo.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import app.echo.android.model.library.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.lang.ref.WeakReference
import androidx.room.InvalidationTracker
import kotlin.coroutines.coroutineContext

internal fun validateFilePlaylistTracks(rows: List<LibraryTrackEntity>) {
    require(rows.size <= FilePlaylistStore.MaxEntries)
    require(rows.all { it.contentUri.length <= 8_192 && it.contentUri.substringBefore(':') in setOf("content", "file") }) {
        "File playlists only accept local file references"
    }
}

internal fun filePlaylistPlaybackWindow(rows: List<LibraryTrackEntity>, anchorId: String, limit: Int): List<LibraryTrackEntity> {
    val index = rows.indexOfFirst { it.id == anchorId }
    if (index < 0) return emptyList()
    val size = limit.coerceIn(1, FilePlaylistStore.MaxEntries)
    return rows.drop(LibraryPlaybackQueuePolicy.windowOffset(index, size)).take(size)
}

/** Preserve picker order and reject overflow before reading metadata or taking new grants. */
fun newFilePlaylistUris(existing: List<String>, requested: List<String>): List<String> {
    require(existing.size <= FilePlaylistStore.MaxEntries && requested.size <= FilePlaylistStore.MaxEntries)
    val known = existing.toHashSet()
    val additions = requested.filter { known.add(it) }
    require(existing.size + additions.size <= FilePlaylistStore.MaxEntries)
    return additions
}

/** Private playlist entries live in their own bounded table, never in library_tracks. */
class FilePlaylistStore(context: Context, private val database: EchoLibraryDatabase) {
    private val context = context.applicationContext
    fun changes(id: String) = database.playlistDao().observePlaylist(id).distinctUntilChanged()

    suspend fun readOrdered(id: String): List<LibraryTrackEntity> =
        read(id, PlaylistOrderStore(context).observe(id).first())

    internal fun pagingSource(id: String, order: PlaylistTrackOrder) = FilePlaylistPagingSource(
        readRows = { read(id, order) }, observe = { source ->
            val tracker = database.invalidationTracker
            val reference = WeakReference(source)
            val observer = object : InvalidationTracker.Observer("file_playlist_entries") {
                override fun onInvalidated(tables: Set<String>) {
                    reference.get()?.invalidate() ?: tracker.removeObserver(this)
                }
            }
            source.registerInvalidatedCallback { tracker.removeObserver(observer) }
            tracker.addObserver(observer)
            if (source.invalid) tracker.removeObserver(observer)
        },
    )

    suspend fun read(id: String, order: PlaylistTrackOrder = PlaylistTrackOrder()): List<LibraryTrackEntity> = withContext(Dispatchers.IO) {
        if (!owns(id)) return@withContext emptyList()
        val rows = database.playlistDao().fileEntries(id).map { decode(it) }
        require(rows.size <= MaxEntries)
        val comparator = when (order.sort) {
            PlaylistTrackSort.Default, PlaylistTrackSort.RecentlyAdded -> null
            PlaylistTrackSort.Title -> compareBy<LibraryTrackEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            PlaylistTrackSort.Artist -> compareBy<LibraryTrackEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy { it.title }
            PlaylistTrackSort.Album -> compareBy<LibraryTrackEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.album.orEmpty() }.thenBy { it.discNumber ?: 1 }.thenBy { it.trackNumber ?: Int.MAX_VALUE }
            PlaylistTrackSort.Duration -> compareBy<LibraryTrackEntity> { it.durationMs }
        }
        val sorted = comparator?.let { rows.sortedWith(it) } ?: rows
        if (order.descending) sorted.reversed() else sorted
    }

    suspend fun create(name: String, uris: List<Uri>, onProgress: (Int, Int) -> Unit = { _, _ -> }): EchoPlaylist =
        createFromTracks(name, readFiles(uris, onProgress))

    suspend fun readFiles(uris: List<Uri>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<LibraryTrackEntity> = withContext(Dispatchers.IO) {
        val id = "files:${UUID.randomUUID()}"
        val unique = uris.distinctBy(Uri::toString)
        require(unique.isNotEmpty() && unique.size <= MaxEntries)
        val rows = ArrayList<LibraryTrackEntity>()
        unique.forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            require(uri.scheme == "content" || uri.scheme == "file")
            readFilePlaylistTrack(context, id, uri)?.let(rows::add)
            onProgress(index + 1, unique.size)
        }
        require(rows.isNotEmpty()) { "No readable audio files" }
        rows
    }

    suspend fun createFromTracks(name: String, rows: List<LibraryTrackEntity>): EchoPlaylist = withContext(Dispatchers.IO) {
        require(rows.isNotEmpty() && rows.size <= MaxEntries)
        validateFilePlaylistTracks(rows)
        createRows("files:${UUID.randomUUID()}", name, rows.distinctBy { it.contentUri })
    }

    private suspend fun createRows(id: String, name: String, rows: List<LibraryTrackEntity>, artwork: String? = null): EchoPlaylist {
        val safeName = LibraryPlaylistPolicy.normalizeName(name) ?: error("Invalid playlist name")
        return database.withTransaction {
            require(database.playlistDao().getPlaylistIdsFromSource(LibrarySource.FilePlaylist.id).size < 100)
            val now = System.currentTimeMillis()
            database.playlistDao().upsertPlaylist(LibraryPlaylistEntity(id, safeName, LibrarySource.FilePlaylist.id, artwork, rows.size, now))
            save(id, rows)
            EchoPlaylist(id, safeName, trackCount = rows.size, artworkUri = artwork, source = LibrarySource.FilePlaylist.id, updatedAtEpochMs = now)
        }
    }

    suspend fun folderUris(tree: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val pending = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }
        var visitedEntries = 0
        val visited = hashSetOf<String>(); val result = ArrayList<Pair<String, Uri>>()
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val parent = pending.removeFirst()
            if (!visited.add(parent)) continue
            require(visited.size <= 2_000) { "Too many folders" }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive(); require(++visitedEntries <= 20_000)
                    val id = cursor.getString(0); val name = cursor.getString(1).orEmpty(); val mime = cursor.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { require(pending.size + visited.size < 2_000); pending.add(id) }
                    else if (isSupportedIncomingAudioFile(name, mime)) {
                        require(result.size < MaxEntries) { "Too many audio files" }
                        result += id to DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    }
                }
            } ?: error("Folder permission is unavailable")
        }
        result.sortedBy { it.first.lowercase(java.util.Locale.ROOT) }.map { it.second }
    }

    suspend fun track(id: String): LibraryTrackEntity? = withContext(Dispatchers.IO) {
        if (!ownsTrack(id)) null else database.playlistDao().fileEntry(id)?.let(::decode)
    }
    suspend fun tracks(ids: List<String>): List<LibraryTrackEntity> = withContext(Dispatchers.IO) {
        ids.distinct().chunked(400).flatMap { database.playlistDao().fileEntriesByIds(it) }.map(::decode)
    }

    suspend fun add(id: String, rows: List<LibraryTrackEntity>): Int = withContext(Dispatchers.IO) { database.withTransaction {
        validateFilePlaylistTracks(rows)
        check(database.playlistDao().getPlaylist(id)?.source == LibrarySource.FilePlaylist.id) { "File playlist no longer exists" }
        val current = read(id); val known = current.mapTo(hashSetOf()) { it.contentUri }
        val additions = rows.filter { known.add(it.contentUri) }
        require(current.size + additions.size <= MaxEntries)
        if (additions.isNotEmpty()) save(id, current + additions)
        additions.size
    } }

    suspend fun remove(id: String, trackId: String): Boolean = withContext(Dispatchers.IO) { database.withTransaction {
        val current = read(id); val next = current.filterNot { it.id == trackId }
        if (current.size == next.size) false else { save(id, next); true }
    } }

    suspend fun move(id: String, trackId: String, targetId: String? = null, direction: Int = 0): Boolean = withContext(Dispatchers.IO) { database.withTransaction {
        val rows = read(id).toMutableList(); val from = rows.indexOfFirst { it.id == trackId }
        val to = if (targetId == null) from + direction else rows.indexOfFirst { it.id == targetId }
        if (from !in rows.indices || to !in rows.indices || from == to) false
        else { rows.add(to, rows.removeAt(from)); save(id, rows); true }
    } }

    suspend fun copy(id: String, name: String, order: PlaylistTrackOrder = PlaylistTrackOrder()): EchoPlaylist = withContext(Dispatchers.IO) {
        val rows = read(id, order); require(rows.isNotEmpty())
        createRows("files:${UUID.randomUUID()}", name, rows, database.playlistDao().getPlaylist(id)?.artworkUri)
    }
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) { database.withTransaction {
        if (!owns(id)) false else { database.playlistDao().deleteFileEntries(id); database.playlistDao().deletePlaylist(id); true }
    } }

    private suspend fun save(id: String, rows: List<LibraryTrackEntity>) {
        validateFilePlaylistTracks(rows)
        val dao = database.playlistDao()
        val playlist = dao.getPlaylist(id) ?: error("Playlist no longer exists")
        require(playlist.source == LibrarySource.FilePlaylist.id)
        dao.deleteFileEntries(id)
        rows.chunked(400).forEachIndexed { chunk, batch ->
            dao.insertFileEntries(batch.mapIndexed { index, row ->
                val document = JSONObject().apply {
                    put("uri", row.contentUri); put("title", row.title.take(512)); put("artist", row.artist.take(512))
                    put("album", row.album?.take(512)); put("albumArtist", row.albumArtist?.take(512)); put("duration", row.durationMs)
                    put("track", row.trackNumber); put("disc", row.discNumber); put("year", row.year)
                    put("mime", row.mimeType); put("size", row.sizeBytes); put("sampleRate", row.sampleRateHz); put("name", row.fileName)
                    put("artwork", row.artworkUri); put("clipStart", row.clipStartMs); put("clipEnd", row.clipEndMs); put("rating", row.rating)
                }.toString()
                require(document.length <= 16_384)
                FilePlaylistEntryEntity(entryId(id, row.contentUri), id, chunk * 400 + index, uriKey(row.contentUri), row.durationMs, document)
            })
        }
        dao.upsertPlaylist(playlist.copy(trackCount = rows.size, updatedAtEpochMs = maxOf(System.currentTimeMillis(), playlist.updatedAtEpochMs + 1)))
    }

    private fun decode(entry: FilePlaylistEntryEntity): LibraryTrackEntity {
        val obj = JSONObject(entry.document)
        fun text(key: String) = obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
        val uri = obj.getString("uri"); require(uri.length <= 8_192 && Uri.parse(uri).scheme in setOf("content", "file"))
        return LibraryTrackEntity(entry.id, uri, obj.optString("title"), obj.optString("artist"), text("album"), text("albumArtist"), text("artwork"),
            obj.optLong("duration"), obj.optInt("track").takeIf { it > 0 }, obj.optInt("disc").takeIf { it > 0 },
            obj.optInt("year").takeIf { it > 0 }, text("mime"), obj.optLong("size"), obj.optInt("sampleRate").takeIf { it > 0 },
            0L, LibrarySource.FilePlaylist.id, fileName = text("name"), clipStartMs = obj.optLong("clipStart"), clipEndMs = obj.optLong("clipEnd"), rating = obj.optInt("rating").coerceIn(0, 5))
    }
    companion object {
        const val MaxEntries = 2_000
        fun owns(id: String) = id.matches(Regex("files:[0-9a-fA-F-]{36}"))
        fun ownsTrack(id: String) = id.startsWith("file:")
        fun uriKey(uri: String) = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        internal fun entryId(playlistId: String, uri: String) = "file:${playlistId.removePrefix("files:")}:${uriKey(uri)}"
    }
}
