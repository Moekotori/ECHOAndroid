package app.echo.android.ui.library

import app.echo.android.model.library.LibraryScanOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    selectedGenre: app.echo.android.model.library.GenreSummary? = null,
    selectedFolder: FolderSummary?,
    selectedPlaylist: EchoPlaylist?,
    onRequestPermission: () -> Unit,
    onScanFolder: (LibraryScanOptions) -> Unit,
    onScanAll: (LibraryScanOptions) -> Unit,
    onImportLyricsForTrack: (EchoTrack) -> Unit,
    onPickTrackArtwork: (EchoTrack) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenGenre: (app.echo.android.model.library.GenreSummary) -> Unit = {},
    onOpenFolder: (FolderSummary) -> Unit,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onCloseDetail: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    val libraryQuery by viewModel.libraryQuery.collectAsStateWithLifecycle()
    val libraryTrackSortMode by viewModel.libraryTrackSortMode.collectAsStateWithLifecycle()
    val libraryAlbumSortMode by viewModel.libraryAlbumSortMode.collectAsStateWithLifecycle()
    val libraryArtistSortMode by viewModel.libraryArtistSortMode.collectAsStateWithLifecycle()
    val libraryFolderSortMode by viewModel.libraryFolderSortMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()
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
    val selectedGenreKey = selectedGenre?.genreKey
    val genreDetailTracks = selectedGenreKey?.let { genreKey ->
        remember(genreKey) { viewModel.genreTrackPaging(genreKey) }.collectAsLazyPagingItems()
    }
    val folderDetailTracks = selectedFolderKey?.let { folderKey ->
        remember(folderKey) { viewModel.folderTrackPaging(folderKey) }.collectAsLazyPagingItems()
    }
    val playlistDetailTracks = selectedPlaylistId?.let { playlistId ->
        remember(playlistId) { viewModel.playlistTrackPaging(playlistId) }.collectAsLazyPagingItems()
    }
    var pendingM3uExportPlaylist by remember { mutableStateOf<EchoPlaylist?>(null) }
    val importM3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importM3uPlaylist)
    }
    val exportM3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        val playlist = pendingM3uExportPlaylist
        pendingM3uExportPlaylist = null
        if (uri != null && playlist != null) {
            viewModel.exportM3uPlaylist(playlist.id, uri)
        }
    }

    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as app.echo.android.EchoApplication
    app.echo.android.feature.library.AlbumOnlineInfoProvider(application.albumOnlineInfo) {
        LibraryScreen(
            hasPermission = hasAudioPermission,
            scanState = scanState,
            libraryQuery = libraryQuery,
            trackSortMode = libraryTrackSortMode,
            albumSortMode = libraryAlbumSortMode,
            artistSortMode = libraryArtistSortMode,
            folderSortMode = libraryFolderSortMode,
            tracks = viewModel.tracks,
            albums = viewModel.albums,
            remoteAlbums = viewModel.remoteAlbums,
            linkedLibraryActive = remoteStatus.connectionState == EchoRemoteConnectionState.Connected &&
                appSettings.echoLinkPreferLinkedLibrary,
            linkedLibraryAvailable = remoteStatus.connectionState == EchoRemoteConnectionState.Connected,
            linkedLibraryState = remoteClient.library,
            selectedLibrarySourceId = appSettings.librarySelectedSource,
            artists = viewModel.artists,
            genres = viewModel.genres,
            folders = viewModel.folders,
            playlists = localPlaylists,
            showTrackAudioInfoTags = appSettings.trackAudioInfoTagsVisible,
            selectedAlbum = selectedAlbum,
            selectedArtist = selectedArtist,
            selectedGenre = selectedGenre,
            selectedFolder = selectedFolder,
            selectedPlaylist = selectedPlaylist,
            albumDetailTracks = albumDetailTracks,
            artistDetailTracks = artistDetailTracks,
            genreDetailTracks = genreDetailTracks,
            folderDetailTracks = folderDetailTracks,
            playlistDetailTracks = playlistDetailTracks,
            onRequestPermission = onRequestPermission,
            onLibraryQueryChange = viewModel::updateLibraryQuery,
            onLibrarySourceChange = viewModel::setLibrarySelectedSource,
            onTrackSortModeChange = viewModel::updateLibraryTrackSortMode,
            onAlbumSortModeChange = viewModel::updateLibraryAlbumSortMode,
            onArtistSortModeChange = viewModel::updateLibraryArtistSortMode,
            onFolderSortModeChange = viewModel::updateLibraryFolderSortMode,
            onScanFolder = onScanFolder,
            onScanAll = onScanAll,
            onCancelScan = viewModel::cancelScan,
            onRefreshLinkedLibrary = { query -> remoteClient.refreshLibrary(query) },
            onOpenLinkedPlaylist = { playlist -> remoteClient.refreshPlaylistTracks(playlist) },
            onOpenLinkedAlbum = { album -> remoteClient.refreshAlbumTracks(album) },
            onRefreshLinkedFolders = { path -> remoteClient.refreshFolders(path) },
            onPlayLinkedTrack = { track ->
                playLinkedEchoTracks(
                    tracks = listOf(track),
                    startIndex = 0,
                    viewModel = viewModel,
                    remoteClient = remoteClient,
                )
            },
            onPlayLinkedQueue = { tracks, startIndex ->
                playLinkedEchoTracks(
                    tracks = tracks,
                    startIndex = startIndex,
                    viewModel = viewModel,
                    remoteClient = remoteClient,
                )
            },
            onPlayLinkedQueueOnPc = { tracks, startIndex ->
                remoteClient.playQueueOnPc(tracks, startIndex)
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
            onOpenGenre = onOpenGenre,
            onPlayGenre = { genre -> viewModel.playGenre(genre.genreKey) },
            onPlayFolder = { folder -> viewModel.playFolder(folder.folderKey) },
            onShuffleFolder = { folder -> viewModel.shuffleFolder(folder.folderKey) },
            onPlayPlaylist = { playlist -> viewModel.playPlaylist(playlist.id) },
            onShufflePlaylist = { playlist -> viewModel.shufflePlaylist(playlist.id) },
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
            onOpenConnect = onOpenConnect,
            cloudLibraryConfigured = !appSettings.webDavServerUrl.isNullOrBlank() ||
                !appSettings.subsonicServerUrl.isNullOrBlank() ||
                !appSettings.jellyfinServerUrl.isNullOrBlank(),
            onImportM3uPlaylist = {
                importM3uLauncher.launch(
                    arrayOf(
                        "audio/x-mpegurl",
                        "audio/mpegurl",
                        "application/vnd.apple.mpegurl",
                        "application/x-mpegurl",
                        "text/plain",
                        "*/*",
                    ),
                )
            },
            onExportM3uPlaylist = { playlist ->
                pendingM3uExportPlaylist = playlist
                val fileName = playlist.name.trim().ifBlank { "playlist" }.replace('/', '-')
                exportM3uLauncher.launch("$fileName.m3u")
            },
        )
    }
}

private fun playLinkedEchoTracks(
    tracks: List<EchoRemoteTrack>,
    startIndex: Int,
    viewModel: EchoAndroidViewModel,
    remoteClient: EchoRemoteClient,
) {
    remoteClient.playTracksOnPhone(
        tracks = tracks,
        startIndex = startIndex,
        onQueueReady = viewModel::playQueue,
        onLyricsReady = viewModel::setEchoLinkLyrics,
    )
}
