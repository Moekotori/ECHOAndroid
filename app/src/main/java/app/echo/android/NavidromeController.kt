package app.echo.android

import android.content.Context
import app.echo.android.data.*
import app.echo.android.feature.library.NavidromeActions
import app.echo.android.model.library.*
import app.echo.android.model.playback.PlaybackQueueState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class NavidromeController(
    context: Context,
    repository: EchoLibraryRepository,
    private val endpoint: () -> SubsonicEndpoint?,
    private val currentQueue: () -> Pair<PlaybackQueueState, Long>,
    private val play: (EchoTrack, Long) -> Unit,
    private val restore: (List<EchoTrack>, Int, Long) -> Unit,
) {
    private val library = SubsonicUserLibrary(repository)
    private val outbox = navidromeOutbox(context)
    private val mutation = Mutex()

    fun actions(captured: SubsonicEndpoint): NavidromeActions {
        fun current(): SubsonicEndpoint = requireNotNull(endpoint()).also {
            check(it.sourceId == captured.sourceId) { "The configured library changed" }
        }
        suspend fun <T> read(block: suspend (SubsonicEndpoint) -> T): T {
            val result = block(current())
            currentCoroutineContext().ensureActive()
            current()
            return result
        }
        suspend fun write(block: suspend (SubsonicEndpoint) -> Unit) = mutation.withLock { read(block) }
        return NavidromeActions(
            sourceId = captured.sourceId,
            load = { query -> read { library.read(it, query) } },
            information = { entry -> read { library.information(it, entry) } },
            execute = { command -> write { library.execute(it, command) } },
            playlist = { id -> read { library.playlist(it, id) } },
            refreshPlaylists = { write { library.refreshPlaylistCatalog(it) } },
            refreshFavorites = { write { library.syncFavorites(it) } },
            saveQueue = { write { server ->
                val (queue, position) = currentQueue()
                val prefix = "${server.sourceId}:song:"
                require(queue.items.isNotEmpty() && queue.items.all { it.id.startsWith(prefix) }) {
                    "The entire queue must belong to this Navidrome library"
                }
                library.saveQueue(server, queue.items.map { it.id.removePrefix(prefix) }, queue.currentIndex, position)
            } },
            restoreQueue = {
                val queue = read { library.queue(it) }
                val tracks = queue.entries.map { requireNotNull(it.track) }
                if (tracks.isNotEmpty()) restore(tracks, queue.currentIndex, queue.positionMs)
            },
            bookmarkCurrent = { comment -> write { server ->
                val (queue, position) = currentQueue()
                val prefix = "${server.sourceId}:song:"
                val id = requireNotNull(queue.currentItem?.id).also { require(it.startsWith(prefix)) }
                library.execute(server, NavidromeCommand.Bookmark(id.removePrefix(prefix), position, comment))
            } },
            play = { entry ->
                current()
                if (entry.kind == NavidromeKind.Radio) {
                    val uri = java.net.URI(entry.url)
                    require(uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
                    play(EchoTrack("radio:navidrome:${captured.sourceId}:${entry.id}", entry.url, entry.name,
                        "Navidrome", source = LibrarySource("radio")), 0)
                } else entry.track?.let { play(it, entry.positionMs) }
            },
            receipts = { read { server -> withContext(Dispatchers.IO) { outbox.receipts(server.sourceId) } } },
            playQueue = { entries, index ->
                val server = current()
                val tracks = entries.map { requireNotNull(it.track) }
                require(tracks.isNotEmpty() && index in tracks.indices && tracks.all { it.source.id == server.sourceId })
                restore(tracks, index, 0)
            },
            resolveReceipt = { key, resolution -> write { server -> withContext(Dispatchers.IO) {
                outbox.resolve(server, key, resolution)
            } } },
        )
    }
}

/** The same store instance coordinates the playback reporter and the management UI. */
internal object NavidromeOutboxOwner {
    private var instance: SubsonicScrobbleOutbox? = null
    @Synchronized fun get(context: Context): SubsonicScrobbleOutbox = instance ?: SubsonicScrobbleOutbox(
        File(context.applicationContext.filesDir, "navidrome-receipts")
    ).also { instance = it }
}
internal fun navidromeOutbox(context: Context) = NavidromeOutboxOwner.get(context)
