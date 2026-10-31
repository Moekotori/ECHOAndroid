package app.echo.android

import app.echo.android.model.playback.EchoVolumeSettings
import app.echo.android.data.EchoVolumeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Main-thread owner of optimistic edits and route changes; persistence never runs on PCM callbacks. */
internal class AppVolumeController(
    scope: CoroutineScope,
    stored: Flow<EchoVolumeSnapshot>,
    devices: Flow<String?>,
    private val save: suspend (EchoVolumeSettings, Long) -> Unit,
    private val apply: (EchoVolumeSettings) -> Unit,
    private val onSaveFailure: (Exception) -> Unit,
) {
    private val current = MutableStateFlow(EchoVolumeSettings())
    val state = current.asStateFlow()
    private var loaded = false
    private var dirty = false
    private var restoreVersion = 0L
    private var device: String? = null
    private val pending = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            devices.distinctUntilChanged().collect { key ->
                device = key
                if (loaded) update(current.value.forDevice(key))
            }
        }
        scope.launch {
            stored.distinctUntilChanged().collect { snapshot ->
                val value = snapshot.volume
                val restored = loaded && snapshot.restoreVersion != restoreVersion
                // An older disk snapshot must not undo a repeat or a later route switch.
                if (loaded && dirty && !restored && value != current.value) return@collect
                restoreVersion = snapshot.restoreVersion
                dirty = false
                loaded = true
                val resolved = value.forDevice(device)
                current.value = resolved
                apply(resolved)
                if (resolved != value) {
                    dirty = true
                    pending.trySend(Unit)
                }
            }
        }
        scope.launch {
            for (ignored in pending) {
                while (withTimeoutOrNull(200) { pending.receive(); true } == true) { /* coalesce key repeats */ }
                if (!dirty) continue // A restore superseded the delayed edit.
                val snapshot = current.value
                val expectedVersion = restoreVersion
                try { save(snapshot, expectedVersion) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { onSaveFailure(error) }
            }
        }
    }

    fun set(value: EchoVolumeSettings) {
        if (!loaded || value.deviceKey != current.value.deviceKey) return
        // UI edits may outlive their page/route. Never import their old device map.
        update(current.value.copy(levelDb = value.levelDb, gainDb = value.gainDb,
            limitDb = value.limitDb, stepDb = value.stepDb, muted = value.muted,
            rememberPerDevice = value.rememberPerDevice,
            // Enabling starts from the audible shared level; disabling keeps the current level.
            deviceKey = if (value.rememberPerDevice) current.value.deviceKey ?: device else null,
        ).normalized.rememberDevice())
    }

    fun adjust(direction: Int): Boolean {
        if (!loaded) return false
        val next = current.value.adjusted(direction)
        set(if (direction > 0) next.copy(muted = false) else next)
        return true
    }

    private fun update(value: EchoVolumeSettings) {
        if (value == current.value) return
        current.value = value
        dirty = true
        apply(value)
        pending.trySend(Unit)
    }
}
