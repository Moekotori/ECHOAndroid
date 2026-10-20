package app.echo.android

import androidx.paging.map
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.RemoteLibraryWriteback
import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.toEchoTrack
import app.echo.android.feature.library.RemoteLibraryWritebackActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RemoteLibraryWritebackController(
    private val repository: EchoLibraryRepository,
    private val endpoint: () -> SubsonicEndpoint?,
) {
    private val writeback = RemoteLibraryWriteback(repository)
    private val mutation = Mutex()
    private val mutableFailure = MutableStateFlow(false)
    val failure = mutableFailure.asStateFlow()
    fun dismissFailure() { mutableFailure.value = false }

    fun actions(configured: SubsonicEndpoint? = endpoint()): RemoteLibraryWritebackActions? {
        val captured = configured ?: return null
        suspend fun run(block: suspend (SubsonicEndpoint) -> Unit) = mutation.withLock {
            val current = requireNotNull(endpoint())
            check(current.sourceId == captured.sourceId) { "The configured library changed" }
            block(current)
        }
        return RemoteLibraryWritebackActions(
            sourceId = captured.sourceId,
            playlists = writeback.playlists(captured.sourceId),
            tracks = { id -> repository.pagedPlaylistTracks(id).map { page -> page.map { it.toEchoTrack() } } },
            candidates = { query -> writeback.candidates(captured.sourceId, query).map { page -> page.map { it.toEchoTrack() } } },
            create = { name -> run { writeback.create(it, name) } },
            rename = { id, name -> run { writeback.rename(it, id, name) } },
            delete = { id -> run { writeback.delete(it, id) } },
            add = { id, song -> run { writeback.add(it, id, song) } },
            remove = { id, song -> run { writeback.remove(it, id, song) } },
        )
    }

    suspend fun setFavorite(id: String, favorite: Boolean): Boolean = try {
        mutation.withLock { writeback.setFavorite(requireNotNull(endpoint()), id, favorite) }
        true
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { mutableFailure.value = true; false }

    suspend fun toggleFavorite(id: String) {
        try {
            mutation.withLock { writeback.toggleFavorite(requireNotNull(endpoint()), id) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableFailure.value = true }
    }
}
