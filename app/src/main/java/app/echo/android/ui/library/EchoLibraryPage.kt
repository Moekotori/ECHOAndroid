package app.echo.android.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.EchoAndroidViewModel
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.data.EchoAppSettings
import app.echo.android.feature.library.LibraryScreen
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteStatus
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary

@Composable
internal fun EchoLibraryPage(
    viewModel: EchoAndroidViewModel,
    remoteClient: EchoRemoteClient,
    remoteStatus: EchoRemoteStatus,
    appSettings: EchoAppSettings,
    hasAudioPermission: Boolean,
    selectedAlbum: AlbumSummary?,
    selectedArtist: ArtistSummary?,
    selectedFolder: FolderSummary?,
    selectedPlaylist: EchoPlaylist?,
    onRequestPermission: () -> Unit,
    onScanFolder: () -> Unit,
    onImportLyricsForTrack: (EchoTrack) -> Unit,
    onPickTrackArtwork: (EchoTrack) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenFolder: (FolderSummary) -> Unit,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onCloseDetail: () -> Unit,
) {
    val libraryQuery by viewModel.libraryQuery.collectAsStateWithLifecycle()
    val libraryTrackSortMode by viewModel.libraryTrackSortMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle(emptyList())
    val selectedAlbumKey = selectedAlbum?.albumKey
    val selectedArtistKey = selectedArtist?.artistKey
    val selectedFolderKey = selectedFolder?.folderKey
    val selectedPlaylistId = selectedPlaylist?.id
    val albumDetailTracks = selectedAlbumKey?.let { albumKey ->
        remember(albumKey) { viewModel.albumTrackPaging(albumKey) }.collectAsLazyPagingItems()
    }
    val artistDetailTracks = selectedArtistKey?.let { artistKey ->
        remember(artistKey) { viewModel.artistTrackPaging(artistKey) }.collectAsLazyPagingItems()
    }
    val folderDetailTracks = selectedFolderKey?.let { folderKey ->
        remember(folderKey) { viewModel.folderTrackPaging(folderKey) }.collectAsLazyPagingItems()
    }
    val playlistDetailTracks = selectedPlaylistId?.let { playlistId ->
        remember(playlistId) { viewModel.playlistTrackPaging(playlistId) }.collectAsLazyPagingItems()
    }

    LibraryScreen(
        hasPermission = hasAudioPermission,
        scanState = scanState,
        libraryQuery = libraryQuery,
        trackSortMode = libraryTrackSortMode,
        tracks = viewModel.tracks,
        albums = viewModel.albums,
        remoteAlbums = viewModel.remoteAlbums,
        linkedLibraryActive = remoteStatus.connectionState == EchoRemoteConnectionState.Connected &&
            appSettings.echoLinkPreferLinkedLibrary,
        linkedLibraryAvailable = remoteStatus.connectionState == EchoRemoteConnectionState.Connected,
        linkedLibraryState = remoteClient.library,
        selectedLibrarySourceId = appSettings.librarySelectedSource,
        artists = viewModel.artists,
        folders = viewModel.folders,
        playlists = localPlaylists,
        showTrackAudioInfoTags = appSettings.trackAudioInfoTagsVisible,
        selectedAlbum = selectedAlbum,
        selectedArtist = selectedArtist,
        selectedFolder = selectedFolder,
        selectedPlaylist = selectedPlaylist,
        albumDetailTracks = albumDetailTracks,
        artistDetailTracks = artistDetailTracks,
        folderDetailTracks = folderDetailTracks,
        playlistDetailTracks = playlistDetailTracks,
        onRequestPermission = onRequestPermission,
        onLibraryQueryChange = viewModel::updateLibraryQuery,
        onLibrarySourceChange = viewModel::setLibrarySelectedSource,
        onTrackSortModeChange = viewModel::updateLibraryTrackSortMode,
        onScanFolder = onScanFolder,
        onScanAll = viewModel::refreshLibrary,
        onCancelScan = viewModel::cancelScan,
        onRefreshLinkedLibrary = { query -> remoteClient.refreshLibrary(query) },
        onOpenLinkedPlaylist = { playlist -> remoteClient.refreshPlaylistTracks(playlist) },
        onPlayLinkedTrack = { track ->
            playLinkedEchoTracks(
                tracks = listOf(track),
                startIndex = 0,
                viewModel = viewModel,
                remoteClient = remoteClient,
                pcHandoffEnabled = appSettings.pcHandoffEnabled,
            )
        },
        onPlayLinkedQueue = { tracks, startIndex ->
            playLinkedEchoTracks(
                tracks = tracks,
                startIndex = startIndex,
                viewModel = viewModel,
                remoteClient = remoteClient,
                pcHandoffEnabled = appSettings.pcHandoffEnabled,
            )
        },
        onPlayTrack = { track, origin -> viewModel.playFromLibrary(track, origin) },
        onPlayNext = viewModel::playNext,
        onEnqueueTrack = viewModel::enqueue,
        onUpdateTrackMetadata = viewModel::updateTrackMetadata,
        onImportLyricsForTrack = onImportLyricsForTrack,
        onPickTrackArtwork = onPickTrackArtwork,
        onPlayAlbum = { album -> viewModel.playAlbum(album.albumKey) },
        onShuffleAlbum = { album -> viewModel.shuffleAlbum(album.albumKey) },
        onPlayArtist = { artist -> viewModel.playArtist(artist.artistKey) },
        onShuffleArtist = { artist -> viewModel.shuffleArtist(artist.artistKey) },
        onPlayFolder = { folder -> viewModel.playFolder(folder.folderKey) },
        onPlayPlaylist = { playlist -> viewModel.playPlaylist(playlist.id) },
        onCreatePlaylist = { name -> viewModel.createLocalPlaylist(name) },
        onRenamePlaylist = { playlist, name -> viewModel.renameLocalPlaylist(playlist.id, name) },
        onDeletePlaylist = { playlist ->
            viewModel.deleteLocalPlaylist(playlist.id)
            if (selectedPlaylist?.id == playlist.id) {
                onCloseDetail()
            }
        },
        onAddTrackToPlaylist = { playlist, track ->
            viewModel.addTrackToLocalPlaylist(playlist.id, track.id)
        },
        onCreatePlaylistAndAddTrack = { name, track ->
            viewModel.createLocalPlaylist(name, addTrackId = track.id)
        },
        onRemoveTrackFromPlaylist = { playlist, track ->
            viewModel.removeTrackFromLocalPlaylist(playlist.id, track.id)
        },
        onReorderPlaylistTracks = { playlist, fromIndex, toIndex ->
            viewModel.reorderLocalPlaylistTracks(playlist.id, fromIndex, toIndex)
        },
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onOpenFolder = onOpenFolder,
        onOpenPlaylist = onOpenPlaylist,
        onCloseDetail = onCloseDetail,
    )
}

private fun playLinkedEchoTracks(
    tracks: List<EchoRemoteTrack>,
    startIndex: Int,
    viewModel: EchoAndroidViewModel,
    remoteClient: EchoRemoteClient,
    pcHandoffEnabled: Boolean,
) {
    val startTrack = tracks.getOrNull(startIndex.coerceAtLeast(0)) ?: return
    if (pcHandoffEnabled) {
        val current = viewModel.playbackStatus.value
        val currentLinkedId = current.track?.id
        val positionMs = if (startTrack.id != null && currentLinkedId == "echo-link:${startTrack.id}") {
            current.positionMs
        } else {
            0L
        }
        remoteClient.handoffToPc(startTrack, positionMs)
        return
    }
    val playable = tracks.filter { it.canPlayOnPhone && !it.id.isNullOrBlank() }
    if (playable.isEmpty()) {
        remoteClient.playTrackOnPc(startTrack)
        return
    }
    remoteClient.playTracksOnPhone(
        tracks = tracks,
        startIndex = startIndex,
        onQueueReady = viewModel::playQueue,
        onLyricsReady = viewModel::setEchoLinkLyrics,
    )
}
