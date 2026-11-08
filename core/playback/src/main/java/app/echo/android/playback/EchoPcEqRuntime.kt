package app.echo.android.playback

import app.echo.android.model.playback.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object EchoPcEqNative {
    @JvmStatic external fun create(rate: Int, channels: Int, configuration: FloatArray, preserve: Boolean): Long
    @JvmStatic external fun update(handle: Long, configuration: FloatArray): Boolean
    @JvmStatic external fun process(handle: Long, buffer: ByteBuffer, offset: Int, frames: Int, channels: Int, enabled: Boolean): Int
    @JvmStatic external fun reset(handle: Long)
    @JvmStatic external fun destroy(handle: Long)
}

internal interface EchoPcEqBackend {
    val available: Boolean
    fun create(rate: Int, channels: Int, configuration: FloatArray, preserve: Boolean): Long
    fun update(handle: Long, configuration: FloatArray): Boolean
    fun process(handle: Long, buffer: ByteBuffer, frames: Int, channels: Int, enabled: Boolean): Int
    fun reset(handle: Long)
    fun destroy(handle: Long)
}
internal object EchoPcEqJniBackend : EchoPcEqBackend {
    override val available: Boolean get() = EchoDspNative.available
    override fun create(rate: Int, channels: Int, configuration: FloatArray, preserve: Boolean) = EchoPcEqNative.create(rate, channels, configuration, preserve)
    override fun update(handle: Long, configuration: FloatArray) = EchoPcEqNative.update(handle, configuration)
    override fun process(handle: Long, buffer: ByteBuffer, frames: Int, channels: Int, enabled: Boolean) = EchoPcEqNative.process(handle, buffer, buffer.position(), frames, channels, enabled)
    override fun reset(handle: Long) = EchoPcEqNative.reset(handle)
    override fun destroy(handle: Long) = EchoPcEqNative.destroy(handle)
}

/** A topology change prepares a new bounded graph; normal edits update fixed native arrays. */
internal class EchoPcEqRuntime(private val report: (Int?, EchoEqProcessingState) -> Unit,
    private val backend: EchoPcEqBackend = EchoPcEqJniBackend,
    private val worker: Executor = executor) {
    private data class Plan(val key: String, val parameters: FloatArray, val active: Boolean, val preserve: Boolean)
    private data class Graph(val key: String, val handle: Long, var applied: FloatArray)
    private val desired = AtomicReference<Plan?>(null)
    private val pending = AtomicReference<Graph?>(null)
    private val retired = ConcurrentLinkedQueue<Graph>()
    private val scheduled = AtomicBoolean(false)
    @Volatile private var handled: Plan? = null
    @Volatile private var preparedKey: String? = null
    @Volatile private var failedKey: String? = null
    @Volatile private var current: Graph? = null
    private var value = EchoEqualizerRuntime()
    private var rate = 0
    private var channels = 0
    private var epoch = 0
    private var processing = false
    private var reported: EchoEqProcessingState? = null
    val available: Boolean get() = backend.available
    val fading: Boolean get() = processing

    fun configure(rate: Int, channels: Int) {
        close(); this.rate = rate; this.channels = channels; epoch++
        update(value, force = true)
    }

    fun update(runtime: EchoEqualizerRuntime, force: Boolean = false) {
        if (!force && value == runtime) return
        value = runtime
        if (!runtime.shouldProcess && desired.get() == null) return
        if (!available || rate !in 8000..384000 || channels !in 1..8) return
        val filters = runtime.filters
        val parameters = FloatArray(6 + filters.size * 12)
        parameters[0] = if (runtime.shouldProcess) 1f else 0f
        parameters[1] = runtime.preampDb
        parameters[2] = if (runtime.audition.active) 1f else 0f
        parameters[3] = runtime.audition.frequencyHz; parameters[4] = runtime.audition.q
        parameters[5] = filters.size.toFloat()
        val layout = StringBuilder("$epoch/$rate/$channels/${runtime.preserveParameters}/")
        filters.forEachIndexed { index, b ->
            val type = when (b.normalizedType()) {
                EchoEqFilterType.LowShelf -> 1; EchoEqFilterType.HighShelf -> 2; EchoEqFilterType.LowPass -> 3
                EchoEqFilterType.HighPass -> 4; EchoEqFilterType.BandStop -> 5; EchoEqFilterType.BandPass -> 6; else -> 0
            }
            val slope = if (EchoParametricEq.isPass(b.type)) EchoParametricEq.snapSlope(b.slope) else 12f
            val q = b.q ?: 0.707f
            val kind = if (type == 6 || (type in 3..4 && slope != 12f)) 2
                else if (runtime.preserveParameters || b.frequencyHz < 20 || b.gainDb !in -24f..12f || q < .1f || q > if (type == 5) 24f else 12f) 1 else 0
            layout.append(kind)
            val at = 6 + index * 12
            parameters[at] = type.toFloat(); parameters[at + 1] = b.frequencyHz; parameters[at + 2] = b.gainDb
            parameters[at + 3] = q; parameters[at + 4] = slope; parameters[at + 5] = if (b.enabled) 1f else 0f
            parameters[at + 6] = b.scope.ordinal.toFloat(); parameters[at + 7] = b.dynamicMode.ordinal.toFloat()
            parameters[at + 8] = b.dynamicThresholdDb; parameters[at + 9] = b.dynamicRatio
            parameters[at + 10] = b.dynamicAttackMs; parameters[at + 11] = b.dynamicReleaseMs
        }
        desired.set(Plan(layout.toString(), parameters, runtime.shouldProcess, runtime.preserveParameters))
        schedule()
    }

    fun process(samples: ByteBuffer, frames: Int): Boolean {
        val plan = desired.get() ?: return false
        if (current == null) pending.getAndSet(null)?.let { if (it.key == plan.key) current = it else retire(it) }
        val graph = current
        if (graph == null) { publish(null, if (failedKey == plan.key) EchoEqProcessingState.Failed else EchoEqProcessingState.Preparing); return false }
        val matching = graph.key == plan.key
        if (matching && graph.applied !== plan.parameters && plan.active) {
            if (!backend.update(graph.handle, plan.parameters)) { publish(null, EchoEqProcessingState.Failed); return false }
            graph.applied = plan.parameters
        }
        val active = plan.active && (matching || pending.get() == null)
        val result = backend.process(graph.handle, samples, frames, channels, active)
        if (result < 0) { publish(null, EchoEqProcessingState.Failed); return false }
        processing = result != 0
        if (!matching && pending.get() != null && !processing) { current = null; retire(graph) }
        publish(if (plan.active && matching) rate else null, if (!plan.active) EchoEqProcessingState.Idle else if (!matching) EchoEqProcessingState.Preparing else EchoEqProcessingState.Native)
        return true
    }

    fun flush() { current?.let { backend.reset(it.handle) }; processing = false; reported = null }
    fun close() {
        desired.set(null); current?.let(::retire); current = null
        pending.getAndSet(null)?.let(::retire); processing = false; epoch++; reported = null
        schedule()
    }
    private fun publish(rate: Int?, state: EchoEqProcessingState) { if (reported != state) { reported = state; report(rate, state) } }
    private fun retire(graph: Graph) { retired.add(graph); schedule() }
    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        worker.execute {
            try {
                do {
                    while (true) { val old = retired.poll() ?: break; backend.destroy(old.handle) }
                    val plan = desired.get()
                    if (plan !== handled) {
                        handled = plan
                        if (plan == null) { preparedKey = null; pending.getAndSet(null)?.let { backend.destroy(it.handle) } }
                        else if (plan.active && plan.key != preparedKey) {
                            val handle = try { backend.create(rate, channels, plan.parameters, plan.preserve) } catch (_: LinkageError) { 0L }
                            if (desired.get()?.key != plan.key) { if (handle != 0L) backend.destroy(handle) }
                            else {
                                preparedKey = if (handle == 0L) null else plan.key
                                failedKey = if (handle == 0L) plan.key else null
                                pending.getAndSet(if (handle == 0L) null else Graph(plan.key, handle, plan.parameters))?.let { backend.destroy(it.handle) }
                            }
                        }
                    }
                } while (desired.get() !== handled || retired.isNotEmpty())
            } finally { scheduled.set(false); if (desired.get() !== handled || retired.isNotEmpty()) schedule() }
        }
    }
    private companion object { val executor = Executors.newSingleThreadExecutor { job -> Thread(job, "echo-pc-eq").apply { isDaemon = true } } }
}
