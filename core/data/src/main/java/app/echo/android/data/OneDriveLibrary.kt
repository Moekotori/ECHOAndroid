package app.echo.android.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress

class OneDriveFolder(val id: String?, val name: String)
class OneDriveFolders(val folders: List<OneDriveFolder>, val next: String?)

/** One read-only connection, with a stable index namespace per Microsoft drive. */
class OneDriveLibrary(context: Context, clientId: String) {
    private val context = context.applicationContext
    private val authorization = OneDriveAuthorization(clientId)
    private val secrets = EchoSecretStore(this.context)
    private val database by lazy { EchoLibraryDatabase.create(this.context) }
    private val dao get() = database.trackDao()
    private val credentials = Mutex()
    private val sourceOperation = Mutex()
    private val graph = OneDriveGraph { accessToken(it) }
    private var connection: Connection? = null
    private var loaded = false
    @Volatile var connectedSourceId: String? = null
        private set
    private val mutableFolder = MutableStateFlow<OneDriveFolder?>(null)
    val folder = mutableFolder.asStateFlow()
    val isAvailable get() = authorization.available

    suspend fun load() {
        withContext(Dispatchers.IO) { credentials.withLock { loadConnection() } }
    }

    suspend fun connect(openBrowser: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        sourceOperation.withLock {
            val tokens = authorization.authorize(openBrowser)
            val source = "onedrive:${oneDriveKey(graph.driveId(tokens.access))}"
            credentials.withLock {
                val old = loadConnection()
                if (old != null && old.source != source) throw IOException("Remove the existing OneDrive connection first")
                val selected = old?.folder ?: OneDriveFolder(null, "OneDrive")
                save(Connection(source, selected, tokens))
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        sourceOperation.withLock {
            credentials.withLock clear@{
                val previous = loadConnection() ?: return@clear
                deleteIndex(previous.source)
                secrets.set(SecretKey, null)
                connection = null
                connectedSourceId = null
                mutableFolder.value = null
            }
        }
    }

    suspend fun folders(parent: OneDriveFolder, next: String?): OneDriveFolders {
        val page = graph.children(parent.id, next, pageSize = 20)
        return OneDriveFolders(page.items.filter { it.optJSONObject("folder") != null }.map(::folderFromItem), page.next)
    }

    private suspend fun deleteIndex(source: String) = database.withTransaction {
        dao.getIdsFromSource(source).chunked(100).forEach { ids ->
            dao.deleteFavoritesByTrackIds(ids)
            dao.deleteScanBatch(ids)
        }
    }

    fun sync(root: OneDriveFolder): Flow<LibraryScanProgress> = flow {
        sourceOperation.withLock {
            val current = credentials.withLock { loadConnection() } ?: throw IOException("OneDrive is disconnected")
            val runId = System.currentTimeMillis()
            val pending = ArrayList<LibraryTrackEntity>(100)
            var inserted = 0
            var updated = 0
            var scanned = 0

            suspend fun flush() {
                if (pending.isEmpty()) return
                val previous = dao.getTracksByIds(pending.map { it.id }).associateBy { it.id }
                val changed = ArrayList<LibraryTrackEntity>(pending.size)
                val unchanged = ArrayList<String>(pending.size)
                for (track in pending) {
                    val old = previous[track.id]
                    val row = track.withPreservedUserMetadata(old).withScanMetadata(runId, old)
                    if (old == null) inserted++
                    else if (row.copy(lastSeenScanRunId = old.lastSeenScanRunId) == old) {
                        unchanged += row.id
                        continue
                    } else updated++
                    changed += row
                }
                dao.upsertScanBatch(changed)
                if (unchanged.isNotEmpty()) dao.markSeen(unchanged, runId)
                pending.clear()
                emit(LibraryScanProgress(LibraryScanPhase.WritingDatabase, scanned, inserted, updated))
            }

            emit(LibraryScanProgress(LibraryScanPhase.QueryingMediaStore))
            scanFolders(root) { item, parent, path ->
                val track = item.toTrack(current.source, parent, path) ?: return@scanFolders
                if (++scanned > 20_000) throw IOException("OneDrive folder exceeds the track limit")
                pending += track
                if (pending.size == 100) flush()
            }
            flush()
            val missing = dao.getMissingTrackIdsFromSource(current.source, runId)
            missing.chunked(100).forEach { dao.deleteScanBatch(it) }
            credentials.withLock { save(requireNotNull(connection).withFolder(root)) }
            emit(LibraryScanProgress(LibraryScanPhase.Completed, scanned, inserted, updated, missing.size, isCompleted = true))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun resolveStream(uri: String): String = withContext(Dispatchers.IO) {
        val path = Uri.parse(uri).pathSegments
        val source = credentials.withLock { loadConnection()?.source }
        if (path.size != 2 || source == null || path[0] != source.substringAfter(':') ||
            dao.getTrackByContentUri(uri)?.source != source
        ) throw IOException("OneDrive track is unavailable")
        OneDriveGraph { rejected -> accessToken(rejected, source) }.streamUrl(path[1])
    }

    private suspend fun scanFolders(root: OneDriveFolder, onItem: suspend (JSONObject, OneDriveFolder, String) -> Unit) {
        val queue = ArrayDeque<Pair<OneDriveFolder, String>>()
        val seen = hashSetOf(root.id)
        queue.add(root to root.name)
        var totalItems = 0
        var totalPages = 0
        while (queue.isNotEmpty()) {
            val (parent, path) = queue.removeFirst()
            var next: String? = null
            val cursors = HashSet<String>()
            do {
                kotlin.coroutines.coroutineContext.ensureActive()
                if (++totalPages > 2_500) throw IOException("OneDrive folder exceeds the pagination limit")
                val page = graph.children(parent.id, next)
                totalItems += page.items.size
                if (totalItems > 100_000) throw IOException("OneDrive folder exceeds the file limit")
                for (item in page.items) {
                    if (item.optJSONObject("folder") == null) {
                        onItem(item, parent, path)
                        continue
                    }
                    val child = folderFromItem(item)
                    if (!seen.add(child.id)) continue
                    if (seen.size > 1_000) throw IOException("OneDrive folder exceeds the directory limit")
                    queue.add(child to "$path/${child.name}")
                }
                next = page.next
                if (next != null && (!cursors.add(next) || cursors.size >= 200)) {
                    throw IOException("OneDrive folder exceeds the pagination limit")
                }
            } while (next != null)
        }
    }

    private suspend fun accessToken(rejected: String?, expectedSource: String? = null): String = credentials.withLock {
        val current = loadConnection() ?: throw IOException("OneDrive is disconnected")
        if (expectedSource != null && current.source != expectedSource) throw IOException("OneDrive account changed")
        val token = current.tokens
        if (token.expiresAt > System.currentTimeMillis() + 60_000 && token.access != rejected) return@withLock token.access
        val refreshed = authorization.refresh(token.refresh)
        save(Connection(current.source, current.folder, refreshed))
        refreshed.access
    }

    private fun loadConnection(): Connection? {
        if (!loaded) {
            connection = secrets.get(SecretKey)?.let { value ->
                runCatching {
                    val json = JSONObject(value)
                    Connection(
                        json.getString("source"),
                        OneDriveFolder(json.optString("folderId").takeIf { it.isNotBlank() }, json.getString("folderName")),
                        OneDriveTokens(json.getString("access"), json.getString("refresh"), json.getLong("expiresAt")),
                    )
                }.getOrNull()
            }
            mutableFolder.value = connection?.folder
            connectedSourceId = connection?.source
            loaded = true
        }
        return connection
    }

    private fun save(value: Connection) {
        val serialized = JSONObject().put("source", value.source)
            .put("folderId", value.folder.id.orEmpty()).put("folderName", value.folder.name)
            .put("access", value.tokens.access).put("refresh", value.tokens.refresh).put("expiresAt", value.tokens.expiresAt)
            .toString()
        secrets.set(SecretKey, serialized)
        if (secrets.get(SecretKey) != serialized) throw IOException("Unable to save OneDrive authorization")
        connection = value
        connectedSourceId = value.source
        mutableFolder.value = value.folder
    }

    private class Connection(val source: String, val folder: OneDriveFolder, val tokens: OneDriveTokens) {
        fun withFolder(folder: OneDriveFolder) = Connection(source, folder, tokens)
    }

    private companion object {
        const val SecretKey = "onedrive_connection"
    }
}

private fun folderFromItem(item: JSONObject): OneDriveFolder = OneDriveFolder(item.getString("id"), item.getString("name"))

private fun JSONObject.nonBlankString(key: String): String? = (opt(key) as? String)?.takeIf { it.isNotBlank() }

private fun oneDriveKey(value: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

private fun JSONObject.toTrack(source: String, parent: OneDriveFolder, path: String): LibraryTrackEntity? {
    val file = optJSONObject("file") ?: return null
    val name = getString("name")
    val mime = file.nonBlankString("mimeType")
    if (!LocalAudioFileTypes.isSupported(name, mime)) return null
    val itemId = getString("id")
    val audio = optJSONObject("audio") ?: JSONObject()
    val artist = audio.nonBlankString("artist") ?: "OneDrive"
    return LibraryTrackEntity(
        id = "$source:file:${oneDriveKey(itemId)}",
        contentUri = Uri.Builder().scheme("echo-onedrive").authority("track")
            .appendPath(source.substringAfter(':')).appendPath(itemId).build().toString(),
        title = audio.nonBlankString("title") ?: name.substringBeforeLast('.', name),
        artist = artist,
        album = audio.nonBlankString("album") ?: parent.name,
        albumArtist = audio.nonBlankString("albumArtist") ?: artist,
        artworkUri = null,
        durationMs = audio.optLong("duration").coerceAtLeast(0),
        trackNumber = audio.optInt("track").takeIf { it > 0 },
        discNumber = audio.optInt("disc").takeIf { it > 0 },
        year = audio.optInt("year").takeIf { it > 0 },
        mimeType = LocalAudioFileTypes.resolvedMimeType(name, mime),
        sizeBytes = optLong("size").coerceAtLeast(0),
        dateModifiedSeconds = runCatching { Instant.parse(getString("lastModifiedDateTime")).epochSecond }.getOrDefault(0),
        source = source,
        relativePath = path,
        fileName = name,
        genre = audio.nonBlankString("genre"),
    )
}
