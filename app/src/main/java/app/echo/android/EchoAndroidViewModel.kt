package app.echo.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.paging.PagingData
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.MediaStoreTrackScanner
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.lyrics.LocalLyricsResolver
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackControlsState
import app.echo.android.model.playback.PlaybackDiagnosticsState
import app.echo.android.model.playback.PlaybackMetadataState
import app.echo.android.model.playback.PlaybackPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class EchoAndroidViewModel(application: Application) : AndroidViewModel(application) {
    private val database = EchoLibraryDatabase.create(application)
    private val repository = EchoLibraryRepository(
        database = database,
        scanner = MediaStoreTrackScanner(application.contentResolver),
    )

    private val libraryController = LibraryController(
        repository = repository,
        scope = viewModelScope,
    )
    private val lyricsController = LyricsController(
        repository = repository,
        lyricsResolver = LocalLyricsResolver(application.contentResolver),
        importedLyricsStore = ImportedLyricsStore(application),
        scope = viewModelScope,
    )
    private val playbackController = PlaybackController(
        application = application,
        scope = viewModelScope,
        onTrackChanged = lyricsController::updateLyricsForTrack,
        onTrackActivated = ::recordRecentPlayback,
    )

    val libraryQuery: StateFlow<String> = libraryController.libraryQuery
    val tracks: Flow<PagingData<EchoTrack>> = libraryController.tracks
    val albums: Flow<PagingData<AlbumSummary>> = libraryController.albums
    val artists: Flow<PagingData<ArtistSummary>> = libraryController.artists
    val libraryStats: Flow<LibraryStats> = libraryController.libraryStats
    val recommendedTracks: Flow<List<EchoTrack>> = libraryController.recommendedTracks
    val recentlyAddedAlbums: Flow<List<AlbumSummary>> = libraryController.recentlyAddedAlbums
    val scanState: StateFlow<LibraryScanProgress> = libraryController.scanState

    val playbackStatus: StateFlow<EchoPlaybackStatus> = playbackController.playbackStatus
    val playbackMetadata: StateFlow<PlaybackMetadataState> = playbackController.playbackMetadata
    val playbackPosition: StateFlow<PlaybackPositionState> = playbackController.playbackPosition
    val playbackControls: StateFlow<PlaybackControlsState> = playbackController.playbackControls
    val playbackDiagnostics: StateFlow<PlaybackDiagnosticsState> = playbackController.playbackDiagnostics
    val lyricsState: StateFlow<EchoLyricsLoadState> = lyricsController.lyricsState

    private val _recentPlaybackAlbums = MutableStateFlow<List<AlbumSummary>>(emptyList())
    val recentPlaybackAlbums: StateFlow<List<AlbumSummary>> = _recentPlaybackAlbums.asStateFlow()
    private val _recentPlaybackArtists = MutableStateFlow<List<ArtistSummary>>(emptyList())
    val recentPlaybackArtists: StateFlow<List<ArtistSummary>> = _recentPlaybackArtists.asStateFlow()

    private val albumPlaybackCounts = mutableMapOf<String, Int>()
    private val artistPlaybackCounts = mutableMapOf<String, Int>()

    fun albumTrackPaging(albumKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.albumTrackPaging(albumKey)

    fun artistTrackPaging(artistKey: String): Flow<PagingData<EchoTrack>> =
        libraryController.artistTrackPaging(artistKey)

    fun refreshLibrary() {
        libraryController.refreshLibrary()
    }

    fun refreshLibraryFolder(treeUri: Uri) {
        libraryController.refreshLibraryFolder(treeUri)
    }

    fun cancelScan() {
        libraryController.cancelScan()
    }

    fun updateLibraryQuery(query: String) {
        libraryController.updateLibraryQuery(query)
    }

    fun play(track: EchoTrack) {
        playbackController.play(track)
    }

    fun playQueue(queue: List<EchoTrack>, startIndex: Int) {
        playbackController.playQueue(queue, startIndex)
    }

    fun playTrackFromLibrary(trackId: String) {
        viewModelScope.launch {
            val queue = libraryController.queueAroundTrack(trackId)
            val startIndex = queue.indexOfFirst { it.id == trackId }.takeIf { it >= 0 } ?: 0
            if (queue.isNotEmpty()) playQueue(queue, startIndex)
        }
    }

    fun openCurrentPlaybackAlbum(onFound: (AlbumSummary) -> Unit) {
        val trackId = playbackController.currentTrackId ?: return
        viewModelScope.launch {
            libraryController.albumSummaryForTrack(trackId)?.let(onFound)
        }
    }

    fun openCurrentPlaybackArtist(onFound: (ArtistSummary) -> Unit) {
        val trackId = playbackController.currentTrackId ?: return
        viewModelScope.launch {
            libraryController.artistSummaryForTrack(trackId)?.let(onFound)
        }
    }

    fun playAlbum(albumKey: String) {
        viewModelScope.launch {
            val queue = libraryController.albumTracksForPlayback(albumKey)
            if (queue.isNotEmpty()) playQueue(queue, 0)
        }
    }

    fun shuffleAlbum(albumKey: String) {
        viewModelScope.launch {
            val queue = libraryController.albumTracksForPlayback(albumKey).shuffled()
            if (queue.isNotEmpty()) {
                playQueue(queue, 0)
                playbackController.enableShuffle()
            }
        }
    }

    fun playArtist(artistKey: String) {
        viewModelScope.launch {
            val queue = libraryController.artistTracksForPlayback(artistKey)
            if (queue.isNotEmpty()) playQueue(queue, 0)
        }
    }

    fun shuffleArtist(artistKey: String) {
        viewModelScope.launch {
            val queue = libraryController.artistTracksForPlayback(artistKey).shuffled()
            if (queue.isNotEmpty()) {
                playQueue(queue, 0)
                playbackController.enableShuffle()
            }
        }
    }

    fun playPause() {
        playbackController.playPause()
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun skipNext() {
        playbackController.skipNext()
    }

    fun skipPrevious() {
        playbackController.skipPrevious()
    }

    fun cycleRepeatMode() {
        playbackController.cycleRepeatMode()
    }

    fun toggleShuffle() {
        playbackController.toggleShuffle()
    }

    fun cyclePlayMode() {
        playbackController.cyclePlayMode()
    }

    fun importLyrics(uri: Uri) {
        lyricsController.importLyrics(uri, playbackController.currentTrackId)
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        lyricsController.adjustLyricsOffset(deltaMs, playbackController.currentTrackId)
    }

    fun resetLyricsOffset() {
        lyricsController.resetLyricsOffset(playbackController.currentTrackId)
    }

    override fun onCleared() {
        libraryController.clear()
        lyricsController.clear()
        playbackController.clear()
        database.close()
        super.onCleared()
    }

    private fun recordRecentPlayback(trackId: String) {
        viewModelScope.launch {
            val album = withContext(Dispatchers.IO) {
                repository.albumSummaryForTrack(trackId)
            }
            val artist = withContext(Dispatchers.IO) {
                repository.artistSummaryForTrack(trackId)
            }
            album?.let { summary ->
                albumPlaybackCounts[summary.albumKey] = (albumPlaybackCounts[summary.albumKey] ?: 0) + 1
                _recentPlaybackAlbums.value = (listOf(summary) + _recentPlaybackAlbums.value)
                    .distinctBy { it.albumKey }
                    .sortedByDescending { albumPlaybackCounts[it.albumKey] ?: 0 }
                    .take(12)
            }
            artist?.let { summary ->
                artistPlaybackCounts[summary.artistKey] = (artistPlaybackCounts[summary.artistKey] ?: 0) + 1
                _recentPlaybackArtists.value = (listOf(summary) + _recentPlaybackArtists.value)
                    .distinctBy { it.artistKey }
                    .sortedByDescending { artistPlaybackCounts[it.artistKey] ?: 0 }
                    .take(8)
            }
        }
    }
}
