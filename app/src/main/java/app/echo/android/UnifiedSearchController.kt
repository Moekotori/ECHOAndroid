package app.echo.android

import app.echo.android.data.*
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.library.EchoUnifiedSearch
import kotlinx.coroutines.*

internal class UnifiedSearchController(private val repository: EchoLibraryRepository) {
    suspend fun search(query: String, pc: EchoRemoteClient): EchoUnifiedSearch = supervisorScope {
        if (query.isBlank()) return@supervisorScope EchoUnifiedSearch()
        val local = async(Dispatchers.IO) { repository.searchLocalLibrary(query) }
        val playlists = async { repository.searchUserPlaylists(query) }
        val moments = async { repository.searchMoments(query) }
        val connected = pc.status.value.connectionState == EchoRemoteConnectionState.Connected
        val remote = async { if (connected) pc.searchTracksSnapshot(query) else emptyList() }
        var result = EchoUnifiedSearch()
        try { val found = local.await(); result = result.copy(tracks = found.tracks.map { it.toEchoTrack() }, albums = found.albums, artists = found.artists) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { result = result.copy(localUnavailable = true) }
        try { result = result.copy(playlists = playlists.await(), moments = moments.await()) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { result = result.copy(localUnavailable = true) }
        try { result = result.copy(pcTracks = remote.await()) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { result = result.copy(pcUnavailable = true) }
        result
    }
}
