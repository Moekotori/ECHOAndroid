package app.echo.android.feature.library

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.LibrarySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LinkedLikedSongsPage(
    playlist: EchoRemotePlaylist,
    loadTracks: suspend () -> List<EchoRemoteTrack>,
    onBack: () -> Unit,
    onPlay: (List<EchoRemoteTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val load by rememberUpdatedState(loadTracks)
    val interactive = LocalEchoPageInteractive.current
    val result by produceState<Result<List<EchoRemoteTrack>>?>(null, attempt, interactive) {
        if (!interactive) return@produceState
        value = null
        value = try {
            Result.success(load())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
    LinkedPlaylistDetailPage(
        playlist = playlist,
        tracks = result?.getOrNull().orEmpty(),
        isLoading = result == null,
        error = if (result?.isFailure == true) stringResource(R.string.feature_library_failed_to_load_playlist_tracks_9130da) else null,
        likedSongs = true,
        onRetry = { attempt++ },
        onBack = onBack,
        onPlay = onPlay,
        modifier = modifier,
    )
}

@Composable
internal fun LinkedPlaylistDetailPage(
    playlist: EchoRemotePlaylist,
    tracks: List<EchoRemoteTrack>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<EchoRemoteTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
    likedSongs: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val play by rememberUpdatedState(onPlay)
    val artwork = playlist.artworkUrl
    val summary = remember(playlist, tracks, likedSongs) {
        EchoPlaylist(
            id = if (likedSongs) EchoPlaylist.LikedSongsId else "echo-link:${playlist.id}",
            name = playlist.name,
            trackCount = maxOf(playlist.trackCount, tracks.size),
            artworkUri = artwork,
            source = LibrarySource.EchoLink.id,
        )
    }
    val covers = remember(artwork, tracks) {
        listOfNotNull(artwork).ifEmpty {
            tracks.asSequence().mapNotNull(EchoRemoteTrack::artworkUrl).distinct().take(4).toList()
        }
    }
    val colors = rememberPlaylistDetailColors()
    val firstPlayable = remember(tracks) { tracks.indexOfFirst { it.canPlayOnPhone && !it.id.isNullOrBlank() } }
    val duration = remember(tracks, summary.trackCount) {
        if (tracks.size == summary.trackCount) tracks.sumOf { it.durationMs.coerceAtLeast(0) } else 0L
    }
    PlaylistDetailLayout(
        onBack = onBack,
        listState = rememberLazyListState(),
        modifier = modifier,
        topBar = {
            PlaylistDetailTopBar(
                title = stringResource(R.string.feature_library_playlists_56bf76),
                canEdit = false,
                onBack = onBack,
                onRename = {},
                onDelete = null,
                removalLabel = R.string.feature_library_playlists_56bf76,
            )
        },
    ) {
        item(key = "playlist-hero", contentType = "header") {
            PlaylistHero(
                playlist = summary,
                coverUris = covers,
                durationMs = duration,
                onPlayAll = { play(tracks, firstPlayable) },
                onShuffle = {
                    scope.launch {
                        val queue = withContext(Dispatchers.Default) {
                            tracks.filter { it.canPlayOnPhone && !it.id.isNullOrBlank() }.shuffled()
                        }
                        if (queue.isNotEmpty()) play(queue, 0)
                    }
                },
                canPlay = firstPlayable >= 0 && !isLoading,
            )
        }
        when {
            isLoading -> item(key = "playlist-loading") {
                PlaylistDetailNotice(stringResource(R.string.feature_library_loading_playlist_eb6248))
            }
            tracks.isEmpty() && !error.isNullOrBlank() -> item(key = "playlist-error") {
                PlaylistDetailNotice(error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.playlist_retry)) }
            }
            tracks.isEmpty() -> item(key = "playlist-empty") {
                PlaylistDetailNotice(stringResource(if (likedSongs) R.string.feature_library_no_liked_songs_yet_heart_a_track_from_ba96cc
                    else R.string.feature_library_this_pc_echo_playlist_has_no_playable_tracks_b480ec))
            }
            else -> itemsIndexed(tracks, key = { index, track -> "playlist-track-$index:${track.id.orEmpty()}" },
                contentType = { _, _ -> "track" }) { index, track ->
                PlaylistTrackRow(
                    track = remember(track) { track.toEchoTrack() },
                    accent = colors.muted,
                    onClick = { play(tracks, index) },
                    modifier = libraryTrackItemMotion(),
                )
            }
        }
    }
}
