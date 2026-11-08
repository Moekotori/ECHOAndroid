package app.echo.android.playback

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal enum class MutePauseAction { None, Pause, Resume }

internal fun mutePauseIsMuted(appMuted: Boolean, strict: Boolean, usbExclusive: Boolean,
    deviceMuted: Boolean, deviceVolume: Int, deviceMaxVolume: Int): Boolean =
    (!strict && appMuted) || (!usbExclusive && (deviceMuted || (deviceMaxVolume > 0 && deviceVolume == 0)))

/** An ephemeral owner: a manual command, another track or another output cancels automatic resume. */
internal class MutePausePolicy {
    private var wasMuted = false
    private var wasEnabled = false
    private var wantedPlay = false
    private var owner: String? = null
    private var ownerEpoch = 0L
    fun interrupt() { owner = null }
    fun update(enabled: Boolean, muted: Boolean, key: String?, wantsPlay: Boolean, canResume: Boolean, epoch: Long = 0L): MutePauseAction {
        val becameMuted = muted && (!wasMuted || !wasEnabled)
        val startedPlaying = wantsPlay && !wantedPlay
        wantedPlay = wantsPlay
        wasMuted = muted; wasEnabled = enabled
        if (!enabled) { owner = null; return MutePauseAction.None }
        if (owner != null && (owner != key || ownerEpoch != epoch)) owner = null
        if (muted && (becameMuted || startedPlaying) && wantsPlay && key != null) { owner = key; ownerEpoch = epoch; return MutePauseAction.Pause }
        if (!muted && owner != null) {
            val resume = owner == key && !wantsPlay && canResume
            owner = null
            return if (resume) MutePauseAction.Resume else MutePauseAction.None
        }
        return MutePauseAction.None
    }
}

@UnstableApi
internal class EchoMutePauseController(context: Context, private val player: Player, scope: CoroutineScope) : Player.Listener, AutoCloseable {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val route = EchoPlaybackProcessRuntime.outputRouteMonitor(context.applicationContext).route
    private val policy = MutePausePolicy()
    private var changing = false
    private var pauseDeadline: Long? = null
    private val settings: Job
    init {
        player.addListener(this)
        settings = scope.launch {
            combine(EchoPlaybackRuntimeOptionsStore.options.map { it.pauseOnMute }.distinctUntilChanged(),
                EchoPlaybackProcessRuntime.volumeSettingsState.map { it.muted }.distinctUntilChanged(),
                EchoPlaybackProcessRuntime.usbExclusiveSinkActive, route,
                EchoPlaybackProcessRuntime.bitPerfectStates) { _, _, _, _, _ -> Unit }
                .combine(EchoPlaybackProcessRuntime.automaticResumeEpoch) { _, _ -> Unit }.collect { refresh() }
        }
    }

    fun onUserCommand(command: Int) {
        if (command in setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP, Player.COMMAND_PREPARE,
                Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) policy.interrupt()
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = refresh()
    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYER_ERROR,
                Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) refresh()
    }

    private fun refresh() {
        if (changing) return
        val runtime = EchoPlaybackProcessRuntime
        val usb = runtime.usbExclusiveSinkActive.value
        val muted = mutePauseIsMuted(runtime.volumeSettings.muted, runtime.usbBitPerfectEnabled, usb,
            player.isDeviceMuted, player.deviceVolume, player.deviceInfo.maxVolume)
        val item = player.currentMediaItem
        val key = item?.let { "${it.mediaId}\u0000${it.queueContext()?.entryId}\u0000${route.value.kind}\u0000${route.value.address}\u0000${route.value.deviceName}\u0000${route.value.deviceNames}\u0000$usb" }
        val canResume = (pauseDeadline?.let { System.currentTimeMillis() < it } != false) &&
            runtime.canContinueAudioInteraction() && audio.mode == AudioManager.MODE_NORMAL &&
            !audio.isMusicActive && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED && player.playerError == null
        val action = policy.update(EchoPlaybackRuntimeOptionsStore.options.value.pauseOnMute, muted, key,
            EchoManualFadePolicy.shouldRememberResume(player.playWhenReady, runtime.manualPlayIntent), canResume, runtime.automaticResumeEpoch.value)
        changing = true
        try {
            when (action) {
                MutePauseAction.Pause -> {
                    pauseDeadline = runtime.sleepTimerEndTimeEpochMs.takeUnless { runtime.sleepTimerFinishingCurrentTrack }
                    player.pause()
                }
                MutePauseAction.Resume -> { pauseDeadline = null; player.play() }
                MutePauseAction.None -> Unit
            }
        } finally { changing = false }
    }

    override fun close() { settings.cancel(); policy.interrupt(); player.removeListener(this) }
}
