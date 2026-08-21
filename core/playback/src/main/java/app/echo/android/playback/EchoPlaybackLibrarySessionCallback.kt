package app.echo.android.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@UnstableApi
internal class EchoPlaybackLibrarySessionCallback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val catalog: () -> EchoPlaybackCatalog,
    private val player: () -> Player?,
    private val session: () -> MediaLibrarySession?,
    private val restorer: EchoPlaybackSessionRestorer,
) : MediaLibrarySession.Callback {
    @Volatile
    private var currentFavorite = false

    fun currentButtons(player: Player? = this.player()) =
        echoPlaybackCommandButtons(
            favorite = currentFavorite,
            repeatMode = player?.repeatMode ?: Player.REPEAT_MODE_OFF,
        )

    fun onPlayerSurfaceChanged(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId.isNullOrBlank() || !EchoPlaybackLibraryIds.isTrackMediaId(mediaId)) {
            currentFavorite = false
            applyButtons(player)
            return
        }
        scope.launch(Dispatchers.IO) {
            currentFavorite = catalog().isFavorite(mediaId)
            applyButtons(player)
        }
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        if (!EchoMediaSessionControllerGate.isAllowed(context, controller, session)) {
            return MediaSession.ConnectionResult.reject()
        }
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(EchoPlaybackSessionCommands.toggleFavorite)
            .add(EchoPlaybackSessionCommands.cycleRepeat)
            .add(EchoPlaybackSessionCommands.openLyrics)
            .build()
        val buttons = currentButtons(session.player)
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setCustomLayout(buttons)
            .setMediaButtonPreferences(buttons)
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        scope.future {
            LibraryResult.ofItem(catalog().root().toMediaItem(), params)
        }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        scope.future {
            val item = catalog().item(mediaId)
            if (item == null) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItem(item.toMediaItem(), null)
            }
        }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        scope.future {
            val sourceId = if (params?.isRecent == true && parentId == EchoPlaybackLibraryIds.ROOT) {
                EchoPlaybackLibraryIds.TRACKS
            } else {
                parentId
            }
            val children = catalog().children(sourceId, page, pageSize).map { it.toMediaItem() }
            LibraryResult.ofItemList(children, params)
        }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> =
        scope.future {
            val results = catalog().search(query, 0, EchoPlaybackLibraryIds.MAX_PAGE_SIZE)
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid(params)
        }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        scope.future {
            val results = catalog().search(query, page, pageSize).map { it.toMediaItem() }
            LibraryResult.ofItemList(results, params)
        }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> =
        scope.future {
            resolvePlayableMediaItems(mediaItems)
        }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        scope.future {
            val requestedIds = mediaItems.map { it.mediaId }
            val resolved = resolvePlayableMediaItems(mediaItems)
            val resolvedStart = if (requestedIds.size == 1) {
                PlaybackSessionPolicy.queueStartIndex(resolved.map { it.mediaId }, requestedIds.first())
            } else {
                startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
            }
            MediaSession.MediaItemsWithStartPosition(
                resolved,
                resolvedStart,
                startPositionMs.coerceAtLeast(0L),
            )
        }

    private suspend fun resolvePlayableMediaItems(mediaItems: List<MediaItem>): List<MediaItem> =
        mediaItems.flatMap { item ->
            val queued = catalog().playableQueue(item.mediaId)
            when {
                queued.isNotEmpty() -> queued.map { it.toMediaItem() }
                item.localConfiguration?.uri != null -> listOf(item)
                else -> emptyList()
            }
        }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        playWhenReady: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        scope.future {
            val snapshot = restorer.restore(userRequestedPlay = playWhenReady)
            withContext(Dispatchers.Main.immediate) {
                val live = player()
                if (live != null && live.mediaItemCount > 0) {
                    MediaSession.MediaItemsWithStartPosition(
                        (0 until live.mediaItemCount).map { live.getMediaItemAt(it) },
                        live.currentMediaItemIndex.coerceAtLeast(0),
                        live.currentPosition.coerceAtLeast(0L),
                    )
                } else {
                    MediaSession.MediaItemsWithStartPosition(
                        snapshot?.queue?.map { it.toMediaItem() }.orEmpty(),
                        snapshot?.currentIndex ?: 0,
                        snapshot?.positionMs ?: 0L,
                    )
                }
            }
        }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: androidx.media3.session.SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        return when (customCommand.customAction) {
            EchoPlaybackSessionCommands.TOGGLE_FAVORITE -> scope.future {
                val mediaId = session.player.currentMediaItem?.mediaId
                if (mediaId.isNullOrBlank() || !EchoPlaybackLibraryIds.isTrackMediaId(mediaId)) {
                    SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
                } else {
                    val liked = catalog().toggleFavorite(mediaId)
                    if (liked == null) {
                        SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                    } else {
                        currentFavorite = liked
                        applyButtons(session.player)
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }
                }
            }
            EchoPlaybackSessionCommands.CYCLE_REPEAT -> {
                val current = session.player
                current.repeatMode = nextPlayerRepeatMode(current.repeatMode)
                applyButtons(current)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            EchoPlaybackSessionCommands.OPEN_LYRICS -> {
                val launched = runCatching {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        ?.apply {
                            action = EchoPlaybackIntents.ACTION_OPEN_LYRICS
                            putExtra(EchoPlaybackIntents.EXTRA_OPEN_LYRICS, true)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                            )
                        } ?: return@runCatching false
                    context.startActivity(launchIntent)
                    true
                }.getOrDefault(false)
                Futures.immediateFuture(
                    SessionResult(
                        if (launched) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_UNKNOWN,
                    ),
                )
            }
            else -> super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun applyButtons(player: Player?) {
        val mediaSession = session() ?: return
        val buttons = currentButtons(player)
        mediaSession.setCustomLayout(buttons)
        mediaSession.setMediaButtonPreferences(buttons)
    }
}

private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    launch(Dispatchers.IO) {
        try {
            result.set(block())
        } catch (cancelled: CancellationException) {
            result.cancel(false)
            throw cancelled
        } catch (error: Throwable) {
            if (!result.setException(error)) {
                throw error
            }
        }
    }
    return result
}
