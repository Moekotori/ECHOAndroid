package app.echo.android.ui.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

import app.echo.android.model.library.LibraryScanOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import app.echo.android.model.library.LibraryTrackSortMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoLinkPlaybackRouter
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
    playbackRouter: EchoLinkPlaybackRouter,
    remoteStatus: EchoRemoteStatus,
    appSettings: EchoAppSettings,
    hasAudioPermission: Boolean,
    selectedAlbum: AlbumSummary?,
    selectedArtist: ArtistSummary?,
    selectedGenre: app.echo.android.model.library.GenreSummary? = null,
    selectedFolder: FolderSummary?,
    selectedPlaylist: EchoPlaylist?,
    outerNavigationOwnsTransition: Boolean,
    detailBackProgress: () -> Float,
    onRequestPermission: () -> Unit,
    onScanFolder: (LibraryScanOptions) -> Unit,
    onAddMusic: () -> Unit,
    onEditTrackTags: (EchoTrack, Boolean) -> Unit,
    onImportLyricsForTrack: (EchoTrack) -> Unit,
    onPickTrackArtwork: (EchoTrack) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenGenre: (app.echo.android.model.library.GenreSummary) -> Unit = {},
    onOpenFolder: (FolderSummary) -> Unit,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onCloseDetail: () -> Unit,
    onLinkedLibraryPageOpenChanged: (Boolean) -> Unit,
    linkedDetailBackEnabled: Boolean,
    onOpenConnect: () -> Unit,
    pageInteractive: Boolean,
    onOpenPlayer: () -> Unit = {},
    bottomInset: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
) {
    EchoLibraryDestinations(viewModel, pageInteractive, bottomInset) { _, openPodcasts ->
    val experienceActions = rememberLibraryExperienceActions(viewModel) { onScanFolder(appSettings.libraryScanOptions) }
    val shortcutContext = androidx.compose.ui.platform.LocalContext.current
    val libraryQuery by viewModel.libraryQuery.collectAsStateWithLifecycle()
    val playbackMetadata by viewModel.playbackMetadata.collectAsStateWithLifecycle()
    val playbackControls by viewModel.playbackControls.collectAsStateWithLifecycle()
    val visiblePlayingId by remember(viewModel, playbackMetadata.track?.id) {
        viewModel.duplicateLibrary.displayTrackId(playbackMetadata.track?.id)
    }.collectAsStateWithLifecycle(initialValue = playbackMetadata.track?.id)
    val remotePlayback by playbackRouter.remoteMode.collectAsStateWithLifecycle()
    val libraryTrackFilter by viewModel.libraryTrackFilter.collectAsStateWithLifecycle()
    val libraryTrackSortMode by viewModel.libraryTrackSortMode.collectAsStateWithLifecycle()
    val libraryAlbumSortMode by viewModel.libraryAlbumSortMode.collectAsStateWithLifecycle()
    val libraryArtistSortMode by viewModel.libraryArtistSortMode.collectAsStateWithLifecycle()
    val libraryFolderSortMode by viewModel.libraryFolderSortMode.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(libraryFolderSortMode, remoteStatus.endpoint?.id,
        remoteStatus.endpoint?.libraryOnly, remoteStatus.connectionState) {
        remoteClient.changeFolderSort(libraryFolderSortMode)
    }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val oneDriveFolder by viewModel.oneDriveLibrary.folder.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()
    var playlistCoverTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val playlistCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = playlistCoverTarget
        playlistCoverTarget = null
        if (uri != null && id != null) viewModel.playlistTools.setArtwork(id, uri)
    }
    val filePlaylistDocuments = rememberFilePlaylistCreation(viewModel, onOpenPlaylist)
    val playlistManagement = remember(viewModel, playlistCoverPicker, filePlaylistDocuments) {
        val tools = viewModel.playlistTools
        app.echo.android.feature.library.PlaylistManagementActions(
            order = tools::order, stats = tools::stats, sort = tools::sort, candidates = tools::candidates,
            memberships = tools::memberships,
            createFilePlaylist = filePlaylistDocuments.create,
            appendFilePlaylist = filePlaylistDocuments.append,
            add = tools::add, remove = tools::remove, move = tools::move, copy = tools::copy,
            moveTo = tools::moveTo, removeUndoable = tools::removeUndoable,
            pickArtwork = { id -> playlistCoverTarget = id; playlistCoverPicker.launch(arrayOf("image/*")) },
        )
    }
    val remotePlaylistManagement = remember(viewModel, appSettings.subsonicServerUrl,
        appSettings.subsonicUsername, appSettings.subsonicPassword) {
        val endpoint = app.echo.android.subsonicEndpointFrom(appSettings)
        viewModel.remoteLibraryTools.actions(endpoint, endpoint?.let { viewModel.navidromeTools.actions(it) })
    }
    val exportPlaylist = rememberPlaylistExport(viewModel)
    val sharePlaylist = rememberPlaylistShare(viewModel.playlistTools)
    val playlistImportState by viewModel.playlistTools.importState.collectAsStateWithLifecycle()
    app.echo.android.feature.library.PlaylistImportDialog(playlistImportState,
        onConfirm = viewModel.playlistTools::confirm, onDismiss = viewModel.playlistTools::dismiss,
        searchCandidates = viewModel.playlistTools::importCandidates, onCorrect = viewModel.playlistTools::correctImport,
        onOpen = onOpenPlaylist)
    val batchContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var batchConsent by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<Boolean>?>(null) }
    val batchWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        batchConsent?.complete(it.resultCode == android.app.Activity.RESULT_OK)
    }
    val batchDelete = rememberLibraryBatchDelete(viewModel)
    val shareOriginal = rememberOriginalAudioShare()
    val batchActions = remember(viewModel, localPlaylists, batchWriteLauncher, batchDelete, shareOriginal) {
        app.echo.android.feature.library.LibraryBatchActions(localPlaylists,
            viewModel.libraryExperience::favorites, viewModel.libraryExperience::addToPlaylist,
            viewModel::batchQueue, viewModel::batchTags,
            delete = batchDelete,
            canDeleteOriginals = app.echo.android.data.LibraryTrackDeletionPolicy::canDeleteOriginals,
            share = shareOriginal,
            writeAccess = { selected ->
                val uris = selected.map { android.net.Uri.parse(it.uri) }.filter { it.authority == "media" }
                if (android.os.Build.VERSION.SDK_INT < 30 || uris.isEmpty()) true else {
                    val response = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    batchConsent = response
                    try {
                        val sender = android.provider.MediaStore.createWriteRequest(batchContext.contentResolver, uris).intentSender
                        batchWriteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                        response.await()
                    } finally { batchConsent = null }
                }
            })
    }
    val selectedAlbumKey = selectedAlbum?.albumKey
    val selectedArtistKey = selectedArtist?.artistKey
    val selectedFolderKey = selectedFolder?.folderKey
    val selectedPlaylistId = selectedPlaylist?.id
    val albumDetailTracks = selectedAlbumKey?.let { albumKey ->
        remember(albumKey) { viewModel.albumTrackPaging(albumKey) }.collectAsLazyPagingItems()
    }
    val albumLiked = selectedAlbumKey?.let { albumKey ->
        androidx.compose.runtime.key(albumKey) {
            remember(viewModel) { viewModel.observeLikedAlbum(albumKey) }
                .collectAsStateWithLifecycle(initialValue = null).value
        }
    }
    var artistQuery by rememberSaveable(selectedArtistKey) { mutableStateOf("") }
    var artistSortName by rememberSaveable(selectedArtistKey) { mutableStateOf(LibraryTrackSortMode.Album.name) }
    val artistSort = LibraryTrackSortMode.valueOf(artistSortName)
    val debouncedArtistQuery by androidx.compose.runtime.key(selectedArtistKey) {
        produceState(artistQuery, artistQuery) {
            delay(250)
            value = artistQuery
        }
    }
    val liveArtist = selectedArtistKey?.let { artistKey ->
        remember(artistKey) { viewModel.observeArtistSummary(artistKey) }
            .collectAsStateWithLifecycle(initialValue = selectedArtist).value
    }
    val artistDetailTracks = selectedArtistKey?.let { artistKey ->
        remember(artistKey, debouncedArtistQuery, artistSort) {
            viewModel.artistTrackPaging(artistKey, debouncedArtistQuery, artistSort)
        }.collectAsLazyPagingItems()
    }
    val artistDetailAlbums = selectedArtistKey?.let { artistKey ->
        remember(artistKey) { viewModel.artistAlbumPaging(artistKey) }.collectAsLazyPagingItems()
    }
    var composerBrowse by remember { mutableStateOf(false) }
    val selectedGenreKey = selectedGenre?.genreKey
    val genreDetailTracks = selectedGenreKey?.let { genreKey ->
        remember(genreKey, composerBrowse) {
            if (composerBrowse) viewModel.composerTrackPaging(genreKey) else viewModel.genreTrackPaging(genreKey)
        }.collectAsLazyPagingItems()
    }
    val folderDetailTracks = selectedFolderKey?.let { folderKey ->
        remember(folderKey) { viewModel.folderTrackPaging(folderKey) }.collectAsLazyPagingItems()
    }
    val playlistDetailTracks = selectedPlaylistId?.let { playlistId ->
        remember(playlistId) { viewModel.playlistTrackPaging(playlistId) }.collectAsLazyPagingItems()
    }
    val importM3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importM3uPlaylist)
    }

    val detailScope = rememberCoroutineScope()
    val openAlbumArtist = remember(selectedAlbum, onOpenArtist) {
        val album = selectedAlbum
        val target = album?.let {
            app.echo.android.data.albumArtistNavigationTarget(it)
        }
        if (album != null && target != null) {
            { onOpenArtist(target) }
        } else {
            null
        }
    }
    val openTrackArtist: (EchoTrack) -> Unit = { track ->
        detailScope.launch {
            val artist = viewModel.artistForTrack(track.id)
                ?: viewModel.artistNavigationTarget(track.artist, track.artworkUri)
                ?: return@launch
            onOpenArtist(artist)
        }
    }
    val openTrackAlbum: (EchoTrack) -> Unit = { track ->
        detailScope.launch {
            viewModel.albumForTrack(track.id)?.let(onOpenAlbum)
        }
    }
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as app.echo.android.EchoApplication
    app.echo.android.feature.library.LibraryPresentationProvider(remember(viewModel) {
        app.echo.android.feature.library.LibraryHiddenTrackActions(viewModel.observeHiddenTracksCount(), viewModel::hiddenTracks,
            viewModel::hideLibraryTracks, viewModel::restoreLibraryTracks, viewModel::restoreAllLibraryTracks)
    }, showFileNames = appSettings.libraryLayout.showFileNames, hiddenTrackActions = appSettings.libraryLayout.hiddenTrackActions,
        trackActionOrder = appSettings.libraryLayout.trackActionOrder,
        showPlayNext = appSettings.libraryLayout.showPlayNext, mediaTools = rememberLibraryMediaTools(viewModel),
        artistArtworkActions = rememberArtistArtworkActions(viewModel)) {
    app.echo.android.feature.library.LibraryDuplicatesProvider(
        viewModel.duplicateLibrary.state, viewModel.duplicateLibrary::analyze, viewModel.duplicateLibrary::cancel,
        viewModel.duplicateLibrary::setEnabled,
    ) {
    app.echo.android.feature.library.TrackVersionsProvider(
        load = viewModel::trackVersions, save = viewModel::setPreferredTrackVersion,
    ) {
    app.echo.android.feature.library.ArtistOnlineInfoProvider(
        loader = application.artistOnlineInfo,
        matcher = app.echo.android.model.library.ArtistSetlistMatcher { viewModel.matchSetlist(it) },
        onPlayMatched = viewModel::playSetlistTracks,
    ) {
    app.echo.android.feature.library.TrackMetadataEditorLauncherProvider(
        onOpen = onEditTrackTags,
    ) {
    app.echo.android.feature.library.AlbumOnlineInfoProvider(application.albumOnlineInfo) {
    app.echo.android.feature.library.LibraryBatchProvider(batchActions,
        pageInteractive = pageInteractive && app.echo.android.design.LocalEchoPageInteractive.current) {
        LibraryScreen(
            libraryLayout = appSettings.libraryLayout,
            onLibraryLayoutChange = viewModel::setLibraryLayout,
            radioContent = { interactive, placeToolbarActions ->
                Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    EchoRadioHost(
                        viewModel, playbackMetadata.mediaId, playbackMetadata.title, playbackControls,
                        onOpenPlayer = onOpenPlayer, interactive = interactive,
                        placeToolbarActions = placeToolbarActions,
                    )
                }
            },
            onOpenPodcasts = openPodcasts,
            hasPermission = hasAudioPermission,
            scanState = scanState,
            libraryQuery = libraryQuery,
            trackSortMode = libraryTrackSortMode,
            trackFilter = libraryTrackFilter,
            lyricsIndexState = viewModel.libraryLyricsIndexState,
            onInspectLyrics = viewModel::startLibraryLyricsInspection,
            onCancelLyricsInspection = viewModel::cancelLibraryLyricsInspection,
            onTrackFilterChange = viewModel::updateLibraryTrackFilter,
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
            linkedLibraryOnly = remoteStatus.endpoint?.libraryOnly == true,
            selectedLibrarySourceId = appSettings.librarySelectedSource,
            artists = viewModel.artists,
            genres = viewModel.genres,
            composers = viewModel.composers,
            folders = viewModel.folders,
            playlists = localPlaylists,
            likedAlbums = viewModel.likedAlbums,
            albumLiked = albumLiked,
            onSetAlbumLiked = viewModel::setLikedAlbum,
            onRestoreBuiltinPlaylists = viewModel::restoreBuiltinPlaylists,
            showTrackAudioInfoTags = appSettings.trackAudioInfoTagsVisible,
            selectedAlbum = selectedAlbum,
            selectedArtist = liveArtist ?: selectedArtist?.copy(albumCount = 0, trackCount = 0, durationMs = 0),
            selectedGenre = selectedGenre,
            selectedFolder = selectedFolder,
            selectedPlaylist = selectedPlaylist,
            outerNavigationOwnsTransition = outerNavigationOwnsTransition,
            detailBackProgress = detailBackProgress,
            albumDetailTracks = albumDetailTracks,
            artistDetailTracks = artistDetailTracks,
            artistDetailAlbums = artistDetailAlbums,
            artistQuery = artistQuery,
            artistSort = artistSort,
            onArtistQueryChange = { artistQuery = it },
            onArtistSortChange = { artistSortName = it.name },
            genreDetailTracks = genreDetailTracks,
            folderDetailTracks = folderDetailTracks,
            playlistDetailTracks = playlistDetailTracks,
            onRequestPermission = onRequestPermission,
            onLibraryQueryChange = viewModel::updateLibraryQuery,
            onLibrarySourceChange = viewModel::setLibrarySelectedSource,
            onTrackSortModeChange = viewModel::updateLibraryTrackSortMode,
            onFindSongLetterIndex = viewModel::localTitleIndex,
            onFindAlbumLetterIndex = viewModel::albumTitleIndex,
            playingTrackId = if (remotePlayback) null else if (selectedAlbum == null && selectedArtist == null &&
                selectedGenre == null && selectedFolder == null && selectedPlaylist == null) visiblePlayingId else playbackMetadata.track?.id,
            onFindPlayingSongIndex = { id -> viewModel.localTrackIndex(id, libraryQuery, libraryTrackSortMode, libraryTrackFilter) },
            isPlaying = playbackControls.isPlaying,
            onAlbumSortModeChange = viewModel::updateLibraryAlbumSortMode,
            onArtistSortModeChange = viewModel::updateLibraryArtistSortMode,
            onFolderSortModeChange = viewModel::updateLibraryFolderSortMode,
            onAddMusic = onAddMusic,
            onCancelScan = viewModel::cancelScan,
            onRefreshLinkedLibrary = { query -> remoteClient.refreshLibrary(query) },
            onLoadMoreLinkedTracks = remoteClient::loadMoreTracks,
            onLoadMoreLinkedAlbums = remoteClient::loadMoreAlbums,
            onLoadMoreLinkedPlaylists = remoteClient::loadMorePlaylists,
            onOpenLinkedPlaylist = { playlist -> remoteClient.refreshPlaylistTracks(playlist) },
            onLoadLinkedPlaylistArtwork = remoteClient::loadPlaylistArtwork,
            onOpenLinkedAlbum = { album -> remoteClient.refreshAlbumTracks(album) },
            onRefreshLinkedFolders = { path -> remoteClient.refreshFolders(path) },
            onChangeLinkedFolderPage = remoteClient::changeFolderPage,
            onPlayLinkedQueue = { tracks, startIndex ->
                playLinkedEchoTracks(
                    tracks = tracks,
                    startIndex = startIndex,
                    viewModel = viewModel,
                    playbackRouter = playbackRouter,
                )
            },
            onPlayLinkedQueueOnPc = if (remoteStatus.endpoint?.libraryOnly == true) null else { tracks, startIndex ->
                playbackRouter.selectRemoteMode(true)
                remoteClient.playQueueOnPc(tracks, startIndex)
            },
            onPlayTrack = { track, origin -> viewModel.playFromLibrary(track, origin) },
            onPlayNext = viewModel::playNext,
            onEnqueueTrack = viewModel::enqueue,
            onUpdateTrackMetadata = viewModel::updateTrackMetadata,
            onImportLyricsForTrack = onImportLyricsForTrack,
            onPickTrackArtwork = onPickTrackArtwork,
            albumArtistAlbums = { album ->
                app.echo.android.data.albumArtistNavigationTarget(album)
                    ?.let { viewModel.artistAlbumPaging(it.artistKey) }
            },
            onPinPlaylistShortcut = { playlist, play -> app.echo.android.library.EchoLibraryShortcuts.pin(shortcutContext,
                app.echo.android.playback.EchoPlaybackLibraryIds.playlist(playlist.id), playlist.name, play) },
            onWriteAlbumNfc = if (viewModel.nfcMusic.supported) { album ->
                viewModel.nfcMusic.prepare(app.echo.android.playback.EchoPlaybackLibraryIds.album(album.albumKey), album.title)
            } else null,
            onWritePlaylistNfc = if (viewModel.nfcMusic.supported) { playlist ->
                viewModel.nfcMusic.prepare(app.echo.android.playback.EchoPlaybackLibraryIds.playlist(playlist.id), playlist.name)
            } else null,
            onPlayAlbum = { album -> viewModel.playAlbum(album.albumKey) },
            onShuffleAlbum = { album -> viewModel.shuffleAlbum(album.albumKey) },
            onPlayArtist = { artist -> viewModel.playArtist(artist.artistKey) },
            onShuffleArtist = { artist -> viewModel.shuffleArtist(artist.artistKey) },
            onOpenGenre = { genre ->
                composerBrowse = false
                onOpenGenre(genre)
            },
            onPlayGenre = { genre ->
                if (composerBrowse) viewModel.playComposer(genre.genreKey) else viewModel.playGenre(genre.genreKey)
            },
            onOpenComposer = { composer ->
                composerBrowse = true
                onOpenGenre(composer)
            },
            onPlayComposer = { composer -> viewModel.playComposer(composer.genreKey) },
            onPlayPlaylist = { playlist -> viewModel.playPlaylist(playlist.id) },
            onShufflePlaylist = { playlist -> viewModel.shufflePlaylist(playlist.id) },
            onCreatePlaylist = { name -> viewModel.createLocalPlaylist(name) },
            experienceActions = experienceActions,
            playlistManagement = playlistManagement,
            remotePlaylistManagement = remotePlaylistManagement,
            onRenamePlaylist = { playlist, name -> viewModel.renameLocalPlaylist(playlist.id, name) },
            onDeletePlaylist = { playlist ->
                viewModel.deleteLocalPlaylist(playlist.id)
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
            onReorderAlbumTracks = { album, fromIndex, toIndex ->
                viewModel.reorderAlbumTracks(album.albumKey, fromIndex, toIndex)
            },
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onOpenAlbumArtist = openAlbumArtist,
            onOpenTrackArtist = openTrackArtist,
            onOpenTrackAlbum = openTrackAlbum,
            onOpenFolder = onOpenFolder,
            onOpenPlaylist = onOpenPlaylist,
            onCloseDetail = onCloseDetail,
            onLinkedLibraryPageOpenChanged = onLinkedLibraryPageOpenChanged,
            linkedDetailBackEnabled = linkedDetailBackEnabled,
            onOpenConnect = onOpenConnect,
            cloudLibraryConfigured = oneDriveFolder != null || !appSettings.webDavServerUrl.isNullOrBlank() ||
                !appSettings.smbServerUrl.isNullOrBlank() ||
                !appSettings.subsonicServerUrl.isNullOrBlank() ||
                !appSettings.jellyfinServerUrl.isNullOrBlank(),
            onImportM3uPlaylist = {
                importM3uLauncher.launch(
                    arrayOf(
                        "audio/x-mpegurl",
                        "audio/mpegurl",
                        "application/vnd.apple.mpegurl",
                        "application/x-mpegurl",
                        "audio/x-scpls",
                        "application/pls+xml",
                        "text/plain",
                        "*/*",
                    ),
                )
            },
            onExportM3uPlaylist = exportPlaylist,
            onSharePlaylist = sharePlaylist,
            onEnqueuePlaylist = { playlist -> viewModel.enqueuePlaylist(playlist.id, playlist.name) },
        )
    }
    }
    }
    }
    }
    }
    }
    }
}

private fun playLinkedEchoTracks(
    tracks: List<EchoRemoteTrack>,
    startIndex: Int,
    viewModel: EchoAndroidViewModel,
    playbackRouter: EchoLinkPlaybackRouter,
) {
    playbackRouter.play(
        tracks = tracks,
        startIndex = startIndex,
        onPhoneQueueReady = viewModel::playQueue,
        onPhoneLyricsReady = viewModel::setEchoLinkLyrics,
    )
}
