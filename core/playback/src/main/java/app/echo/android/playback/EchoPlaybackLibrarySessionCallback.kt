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
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommands
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
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
    private val nextUpQueue: () -> NextUpQueueController? = { null },
    private val abLoop: () -> EchoAbLoopController? = { null },
    private val onUserCommand: (Int) -> Unit = {},
    private val onManualToggle: () -> Boolean = { false },
) : MediaLibrarySession.Callback {
    @Volatile
    private var currentFavorite = false

    fun currentButtons(player: Player? = this.player()) =
        echoPlaybackCommandButtons(
            context = context,
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
        scope.launch {
            currentFavorite = withContext(Dispatchers.IO) {
                catalog().isFavorite(mediaId)
            }
            applyButtons(player)
        }
    }

    override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
        val event = if (android.os.Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
        else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? android.view.KeyEvent)
        if (event?.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            event.keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            onUserCommand(Player.COMMAND_PLAY_PAUSE)
            if (onManualToggle()) return true
        }
        if (event?.action == android.view.KeyEvent.ACTION_DOWN) {
            val command = when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> Player.COMMAND_PLAY_PAUSE
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> Player.COMMAND_STOP
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> Player.COMMAND_SEEK_TO_NEXT
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> Player.COMMAND_SEEK_TO_PREVIOUS
                else -> null
            }
            command?.let(onUserCommand)
        }
        return false
    }

    @Deprecated("Media3 compatibility callback")
    override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
        onUserCommand(playerCommand)
        return SessionResult.RESULT_SUCCESS
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        if (!EchoMediaSessionControllerGate.isAllowed(context, controller, session)) {
            if (EchoMediaSessionControllerGate.isLegacyTransportController(controller.uid, controller.controllerVersion)) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(SessionCommands.EMPTY)
                    .setAvailablePlayerCommands(echoLegacyTransportCommands())
                    .setCustomLayout(emptyList())
                    .setMediaButtonPreferences(emptyList())
                    .build()
            }
            EchoErrorLog.record(
                EchoErrorSource.Playback,
                "Media controller connection rejected.",
                detail = "package=${controller.packageName}, uid=${controller.uid}, " +
                    "trusted=${controller.isTrusted}, version=${controller.controllerVersion}",
            )
            return MediaSession.ConnectionResult.reject()
        }
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(EchoPlaybackSessionCommands.editQueue)
            .add(EchoPlaybackSessionCommands.setAbLoop)
            .add(EchoPlaybackSessionCommands.addNextUp)
            .add(EchoPlaybackSessionCommands.playAlbum)
            .add(EchoPlaybackSessionCommands.clearNextUp)
            .add(EchoPlaybackSessionCommands.toggleFavorite)
            .add(EchoPlaybackSessionCommands.cycleRepeat)
            .add(EchoPlaybackSessionCommands.openLyrics)
            .add(EchoPlaybackSessionCommands.openLockLyrics)
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
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
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
            resolvePlayableMediaItems(mediaItems).map { if (it.queueContext() == null) it.asQueueEntry() else it }
        }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        scope.future {
            mediaItems.singleOrNull()?.voiceSearchRequest()?.let { request ->
                val result = catalog().voiceQueue(request)
                if (result.items.isEmpty()) throw UnsupportedOperationException(context.getString(R.string.voice_search_no_result))
                return@future MediaSession.MediaItemsWithStartPosition(
                    result.items.map { it.toMediaItem().asQueueEntry() }, result.startIndex, 0L)
            }
            val requestedIds = mediaItems.map { it.mediaId }
            val resolved = resolvePlayableMediaItems(mediaItems)
            val resolvedStart = PlaybackCatalogPolicy.resolvedStartIndex(
                requestedIds = requestedIds,
                resolvedIds = resolved.map { it.mediaId },
                startIndex = startIndex,
            )
            MediaSession.MediaItemsWithStartPosition(
                resolved.map { if (it.queueContext() == null) it.asQueueEntry(source = it.mediaMetadata.albumTitle?.toString()) else it },
                resolvedStart,
                startPositionMs.coerceAtLeast(0L),
            )
        }

    private suspend fun resolvePlayableMediaItems(mediaItems: List<MediaItem>): List<MediaItem> =
        mediaItems.flatMap { item ->
            item.voiceSearchRequest()?.let { request ->
                return@flatMap catalog().voiceQueue(request).items.map { it.toMediaItem() }
            }
            val playUri = item.localConfiguration?.uri?.toString()
            if (
                !PlaybackCatalogPolicy.shouldExpandCatalogQueue(
                    mediaId = item.mediaId,
                    hasPlayUri = PlaybackCatalogPolicy.hasPlayableUri(playUri),
                )
            ) {
                return@flatMap if (PlaybackCatalogPolicy.hasPlayableUri(playUri)) listOf(item) else emptyList()
            }
            val queued = catalog().playableQueue(item.mediaId)
            when {
                queued.isNotEmpty() -> queued.map { it.toMediaItem() }
                PlaybackCatalogPolicy.hasPlayableUri(playUri) -> listOf(item)
                else -> emptyList()
            }
        }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        playWhenReady: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        scope.future {
            val snapshot = restorer.restore(userRequestedPlay = false)
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
            EchoPlaybackSessionCommands.PLAY_ALBUM -> {
                val queue = nextUpQueue()
                val items = args.getParcelableArrayList<Bundle>("items")?.mapNotNull {
                    runCatching { MediaItem.fromBundle(it) }.getOrNull()
                }.orEmpty()
                if (queue == null || items.isEmpty() || items.any { it.localConfiguration?.uri == null }) {
                    Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                } else {
                    onUserCommand(Player.COMMAND_SET_MEDIA_ITEM)
                    queue.playAlbum(items, args.getInt("startIndex"), args.getBoolean("shuffle"))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            EchoPlaybackSessionCommands.SET_AB_LOOP -> {
                val accepted = abLoop()?.set(args.getString("trackId"), args.getLong("startMs"), args.getLong("endMs")) == true
                Futures.immediateFuture(SessionResult(if (accepted) SessionResult.RESULT_SUCCESS else SessionError.ERROR_BAD_VALUE))
            }
            EchoPlaybackSessionCommands.EDIT_QUEUE -> {
                val queue = nextUpQueue()
                if (args.getString("action") == EchoPlaybackSessionCommands.EDIT_REMOVE_HIDDEN_TRACKS) {
                    val ids = args.getStringArrayList("trackIds").orEmpty()
                    if (controller.uid != android.os.Process.myUid() || queue == null || ids.isEmpty() || ids.size > 200 || ids.any { it.isBlank() || it.length > 8192 })
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    queue.removeHiddenTracks(ids.toSet())
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (args.getString("action") == EchoPlaybackSessionCommands.EDIT_REMOVE_DELETED_FILES) {
                    if (controller.uid != android.os.Process.myUid()) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                    }
                    val uris = args.getStringArrayList("uris").orEmpty()
                    if (queue == null || !DeletedQueueFilesPolicy.validSources(uris)) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    }
                    queue.removeDeletedFiles(uris.toSet())
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (args.getString("action") == "play") onUserCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                if (args.getString("action") == "restore_order") queue?.restoreOrder(args.getIntArray("order")?.toList().orEmpty())
                else queue?.edit(args.getString("action").orEmpty(), args.getString("entry").orEmpty(), args.getString("target"))
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            EchoPlaybackSessionCommands.ADD_NEXT_UP -> {
                val bundle = args.getBundle("item")
                val queue = nextUpQueue()
                val item = bundle?.let { runCatching { MediaItem.fromBundle(it) }.getOrNull() }
                if (queue == null || item == null || item.localConfiguration?.uri == null) {
                    Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                } else {
                    queue.add(item, args.getBoolean("first"))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            EchoPlaybackSessionCommands.CLEAR_NEXT_UP -> {
                nextUpQueue()?.clearPending()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            EchoPlaybackSessionCommands.TOGGLE_FAVORITE -> scope.future {
                val mediaId = withContext(Dispatchers.Main.immediate) {
                    session.player.currentMediaItem?.mediaId
                }
                if (mediaId.isNullOrBlank() || !EchoPlaybackLibraryIds.isTrackMediaId(mediaId)) {
                    SessionResult(SessionError.ERROR_INVALID_STATE)
                } else {
                    val liked = catalog().toggleFavorite(mediaId)
                    if (liked == null) {
                        SessionResult(SessionError.ERROR_NOT_SUPPORTED)
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
            EchoPlaybackSessionCommands.OPEN_LOCK_LYRICS -> {
                val launched = runCatching {
                    val lockIntent = Intent().setClassName(
                        context.packageName,
                        "app.echo.android.lock.EchoLockLyricsActivity",
                    ).apply {
                        action = EchoPlaybackIntents.ACTION_OPEN_LOCK_LYRICS
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        )
                    }
                    context.startActivity(lockIntent)
                    true
                }.getOrDefault(false)
                Futures.immediateFuture(
                    SessionResult(
                        if (launched) SessionResult.RESULT_SUCCESS else SessionError.ERROR_UNKNOWN,
                    ),
                )
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
                        if (launched) SessionResult.RESULT_SUCCESS else SessionError.ERROR_UNKNOWN,
                    ),
                )
            }
            else -> super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun applyButtons(player: Player?) {
        scope.launch(Dispatchers.Main.immediate) {
            val mediaSession = session() ?: return@launch
            val buttons = currentButtons(player)
            mediaSession.setCustomLayout(buttons)
            mediaSession.setMediaButtonPreferences(buttons)
        }
    }
}

private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    val job = launch(Dispatchers.IO) {
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
    result.addListener({ if (result.isCancelled) job.cancel() }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
    return result
}
