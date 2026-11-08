package app.echo.android.playback

import app.echo.android.model.playback.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One processing owner. The observable carries graph facts, not persisted switch intent. */
object EchoSoundEffectRuntime {
    @Volatile private var owner: Any? = null
    private val mutable = MutableStateFlow(EchoSoundEffectRuntimeStatus())
    val status = mutable.asStateFlow()
    internal fun claim(token: Any) { owner = token }
    internal fun publish(token: Any, state: EchoSoundEffectState, preset: EchoSoundEffectPreset?, rate: Int, channels: Int, latency: Int = 0) {
        if (owner !== token) return
        val old = mutable.value
        if (old.state == state && old.preset == preset && old.sampleRateHz == rate && old.channels == channels && old.latencyFrames == latency) return
        mutable.value = EchoSoundEffectRuntimeStatus(state, preset, rate, channels, latency)
    }
}

/** Preparation/reclamation never run on the playback thread; live handles have one audio owner. */
internal class EchoSoundEffectProcessor(
    private val backend: EchoSoundEffectBackend = EchoSoundEffectJniBackend,
    private val executor: Executor = preparation,
) {
    private data class Request(val preset: EchoSoundEffectPreset, val rate: Int, val channels: Int)
    private data class Graph(val request: Request, val handle: Long)
    private val requested = AtomicReference<Request?>(null)
    private val pending = AtomicReference<Graph?>(null)
    private val failed = AtomicReference<Request?>(null)
    private val scheduled = AtomicBoolean(false)
    private val retired = ConcurrentLinkedQueue<Graph>()
    @Volatile private var handled: Request? = null
    private var current: Graph? = null
    private val metadata = IntArray(3)
    private var rate = 0
    private var channels = 0
    val needsProcessing: Boolean get() = current != null || pending.get() != null
    val tailFrames: Int get() = if (current != null) metadata[0] else 0

    fun configure(sampleRate: Int, channelCount: Int) {
        close()
        rate = sampleRate; channels = channelCount
        EchoSoundEffectRuntime.claim(this)
    }

    fun update(settings: EchoSoundEffectSettings) {
        if (!settings.enabled) {
            if (requested.getAndSet(null) != null) schedule()
            if (current == null) publish(EchoSoundEffectState.Off, null)
            return
        }
        if (rate !in 8_000..192_000 || channels !in 1..8 ||
            (channels == 1 && settings.preset in monoBypass)) {
            if (requested.getAndSet(null) != null) schedule()
            publish(EchoSoundEffectState.UnsupportedFormat, settings.preset)
            return
        }
        val before = requested.get()
        if (before?.preset == settings.preset && before.rate == rate && before.channels == channels) return
        requested.set(Request(settings.preset, rate, channels))
        failed.set(null)
        publish(EchoSoundEffectState.Preparing, settings.preset)
        schedule()
    }

    fun process(samples: ByteBuffer, frames: Int): Boolean {
        val expected = requested.get()
        if (current == null) {
            pending.getAndSet(null)?.let { graph ->
                if (graph.request === expected) current = graph else retire(graph)
            }
        }
        val graph = current ?: return false
        // Keep the old effect until its replacement is ready, then fade it fully to dry.
        val enabled = graph.request === expected || (expected != null && pending.get() == null && failed.get() !== expected)
        if (!backend.process(graph.handle, samples, frames, channels, enabled, metadata)) {
            current = null; retire(graph); metadata.fill(0)
            publish(EchoSoundEffectState.Failed, expected?.preset)
            return false
        }
        if (!enabled && metadata[2] == 1) {
            current = null; retire(graph); metadata.fill(0)
            publish(when { expected == null -> EchoSoundEffectState.Off; failed.get() === expected -> EchoSoundEffectState.Failed
                else -> EchoSoundEffectState.Preparing }, expected?.preset)
        } else publish(if (graph.request === expected) EchoSoundEffectState.Active else EchoSoundEffectState.Preparing,
            graph.request.preset, metadata[1])
        return true
    }

    fun flush() {
        current?.let { backend.reset(it.handle) }
        metadata.fill(0)
        requested.get()?.let { publish(EchoSoundEffectState.Preparing, it.preset) }
    }

    fun close() {
        requested.set(null)
        current?.let { retire(it) }; current = null
        pending.getAndSet(null)?.let { retire(it) }
        metadata.fill(0)
        publish(EchoSoundEffectState.Off, null)
        schedule()
    }

    private fun retire(graph: Graph) { retired.add(graph); schedule() }
    private fun publish(state: EchoSoundEffectState, preset: EchoSoundEffectPreset?, latency: Int = 0) =
        EchoSoundEffectRuntime.publish(this, state, preset, rate, channels, latency)

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        executor.execute {
            try {
                do {
                    while (true) { val old = retired.poll() ?: break; backend.destroy(old.handle) }
                    val next = requested.get()
                    if (next !== handled) {
                        handled = next
                        pending.getAndSet(null)?.let { backend.destroy(it.handle) }
                        if (next != null) {
                            val handle = try { backend.create(next.preset.id, next.rate, next.channels) } catch (_: Exception) { 0L }
                            if (requested.get() !== next) { if (handle != 0L) backend.destroy(handle) }
                            else if (handle == 0L) { failed.set(next); publish(EchoSoundEffectState.Failed, next.preset) }
                            else pending.set(Graph(next, handle))
                        }
                    }
                } while (requested.get() !== handled || retired.isNotEmpty())
            } finally {
                scheduled.set(false)
                if (requested.get() !== handled || retired.isNotEmpty()) schedule()
            }
        }
    }

    private companion object {
        val monoBypass = setOf(EchoSoundEffectPreset.Headphones, EchoSoundEffectPreset.AutoPan)
        val preparation: Executor = Executors.newSingleThreadExecutor { job -> Thread(job, "echo-sound-effects").apply { isDaemon = true } }
    }
}
