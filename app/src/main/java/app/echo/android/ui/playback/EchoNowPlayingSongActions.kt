package app.echo.android.ui.playback

import android.net.Uri
import android.widget.Toast
import app.echo.android.data.EmbeddedTagWriteResult
import app.echo.android.data.OriginalAudioSharePolicy
import app.echo.android.feature.player.CoverSaveOutcome
import app.echo.android.feature.player.R as PlayerR
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.EchoApplication
import app.echo.android.feature.library.AddToPlaylistDialog
import app.echo.android.feature.library.TrackInfoDialog
import app.echo.android.feature.library.TrackInfoAudioFormat
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.feature.player.NowPlayingSongActions
import app.echo.android.feature.player.OnlineSongAssetsSheet
import app.echo.android.model.library.*
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.radio.EchoRadioStation
import kotlinx.coroutines.CancellationException
import app.echo.android.ui.library.rememberOriginalAudioShare

@Composable
internal fun rememberNowPlayingSongActions(
    viewModel: EchoAndroidViewModel,
    ref: EchoTrackRef?,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenFolder: (FolderSummary) -> Unit,
    onOpenEqualizer: () -> Unit,
    onSearchLyrics: () -> Unit,
    removal: PlayerSongRemovalActions,
    diagnostics: EchoPlaybackDiagnostics,
): NowPlayingSongActions {
    val context = LocalContext.current
    val application = context.applicationContext as EchoApplication
    val shareOriginal = rememberOriginalAudioShare(showOptions = false)
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var indexedTrack by remember(ref?.id) { mutableStateOf<EchoTrack?>(null) }
    var album by remember(ref?.id) { mutableStateOf<AlbumSummary?>(null) }
    var folder by remember(ref?.id) { mutableStateOf<FolderSummary?>(null) }
    var showPlaylist by remember(ref?.id) { mutableStateOf(false) }
    var showInfo by remember(ref?.id) { mutableStateOf(false) }
    var showOnline by remember(ref?.id) { mutableStateOf(false) }
    LaunchedEffect(ref?.id, ref?.artworkUri, reload, removal.revision) {
        val id = ref?.id
        if (id == null) { loading = false; return@LaunchedEffect }
        if (EchoRadioStation.isRadio(id)) { loading = false; return@LaunchedEffect }
        loading = true; loadFailed = false
        try {
            indexedTrack = viewModel.playerLibraryTrack(id)
            album = viewModel.albumForTrack(id)
            folder = viewModel.playerTrackFolder(id)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true }
        finally { loading = false }
    }
    val track = indexedTrack ?: ref?.let {
        EchoTrack(it.id, it.uri, it.title, it.artist, album = it.album, artworkUri = it.artworkUri,
            durationMs = it.durationMs, sampleRateHz = it.sampleRateHz, trackNumber = it.trackNumber,
            discNumber = it.discNumber, source = LibrarySource(it.sourceId ?: LibrarySource.Unknown.id))
    }
    if (showPlaylist && track != null) {
        val playlists by viewModel.localPlaylists.collectAsStateWithLifecycle()
        val memberships by remember(viewModel, track.id) {
            viewModel.playlistTools.memberships(track.id)
        }.collectAsStateWithLifecycle(initialValue = emptySet())
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        fun submit(operation: suspend () -> Boolean) {
            if (busy) return
            busy = true; error = null
            scope.launch {
                try {
                    if (operation()) {
                        Toast.makeText(context, PlayerR.string.song_playlist_added, Toast.LENGTH_SHORT).show()
                        showPlaylist = false
                    } else error = context.getString(PlayerR.string.song_playlist_failed)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = context.getString(PlayerR.string.song_playlist_failed) }
                finally { busy = false }
            }
        }
        AddToPlaylistDialog(playlists = playlists, onDismiss = { showPlaylist = false },
            containingPlaylistIds = memberships,
            busy = busy, error = error, awaitResult = true,
            onSelectPlaylist = { playlist -> submit { viewModel.addPlayerSongToPlaylist(playlist.id, track.id) } },
            onCreatePlaylist = { name -> submit { viewModel.createPlayerPlaylist(name, track.id) } })
    }
    if (showInfo && track != null) TrackInfoDialog(
        track, onDismiss = { showInfo = false },
        mediaTools = app.echo.android.ui.library.rememberLibraryMediaTools(viewModel),
        audioFormat = TrackInfoAudioFormat(diagnostics.codec, diagnostics.sampleRateHz,
            diagnostics.bitDepth, diagnostics.channelCount, diagnostics.bitrate),
    )
    if (showOnline && track != null) OnlineSongAssetsSheet(
        track = track,
        searchCover = { application.onlineTrackArtwork.search(track) },
        applyCover = { uri ->
            val saved = application.onlineTrackArtwork.save(uri)
            val result = viewModel.savePlayerArtwork(track.id, Uri.parse(saved))
            reload++
            when {
                !result.indexUpdated -> CoverSaveOutcome.Failed
                result.fileWrite is EmbeddedTagWriteResult.Written -> CoverSaveOutcome.Saved
                else -> CoverSaveOutcome.LibraryOnly
            }
        },
        onLyrics = onSearchLyrics,
        onDismiss = { showOnline = false },
    )
    val song = track?.takeUnless { EchoRadioStation.isRadio(it.id) }
    val removable = indexedTrack?.takeUnless { it.source == LibrarySource.EchoLink || EchoRadioStation.isRadio(it.id) }
    return NowPlayingSongActions(
        loading = loading,
        loadFailed = loadFailed,
        retry = { reload++ },
        addToPlaylist = if (indexedTrack != null) ({ showPlaylist = true }) else null,
        share = song?.takeIf(OriginalAudioSharePolicy::supports)?.let { shared -> { shareOriginal(listOf(shared)) } },
        equalizer = onOpenEqualizer,
        album = album?.let { target -> { onOpenAlbum(target) } },
        info = track?.let { { showInfo = true } },
        onlineAssets = if (song != null && indexedTrack != null) ({ showOnline = true }) else null,
        folder = folder?.let { target -> { onOpenFolder(target) } },
        hide = removable?.let { selected -> { removal.hide(selected) } },
        delete = removable?.let { selected -> { removal.delete(selected) } },
    )
}
