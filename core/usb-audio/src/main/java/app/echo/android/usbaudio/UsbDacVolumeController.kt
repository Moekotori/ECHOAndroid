package app.echo.android.usbaudio

import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class UsbDacVolumeState(val requested: String = "software", val effective: String = "software",
    val checked: Boolean = false, val readbackVerified: Boolean = false, val hardwareDb: Float? = null,
    val handoff: Boolean = false, val reason: String? = null)

/** Dedicated control worker. No control transfers or sleeping in writePcm/the packing loop. */
internal class UsbDacVolumeController(raw: ByteArray, format: UsbAudioStreamingFormat,
    private val transfer: UsbAudioControlTransfer, percent: Int, mode: String, sampleRate: Int,
    private val settleMs: Long) : AutoCloseable {
    val ramp = UsbPcmGainRamp(percent.coerceIn(0, 100) / 100f, sampleRate)
    @Volatile var state = UsbDacVolumeState(requested = mode)
        private set
    @Volatile var fatal = false
        private set
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "echo-dac-volume").apply { isDaemon = true } }
    private val controlLock = Any()
    private val unit = UsbVolumeTopology.find(raw, format)
    private var control: UsbFeatureVolumeControl? = null
    private var range: UsbVolumeRange? = null
    private var originalDb: Int? = null
    private var currentDb: Int? = null
    private var originalMute: Boolean? = null
    private var changedHardware = false
    private var failedHardware = false
    private var failureReason: String? = null
    @Volatile private var closed = false
    @Volatile private var playing = false
    fun setPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        if (value) desired.let { request(it.mode, it.percent) }
    }
    @Volatile private var desired = Desired(mode, percent.coerceIn(0, 100))
    private var revision = 0L
    private var scheduled = false
    init { request(mode, percent) }
    private data class Desired(val mode: String, val percent: Int)
    @Synchronized fun request(mode: String, percent: Int) {
        if (closed) return
        desired = Desired(mode, percent.coerceIn(0, 100)); revision++
        if (scheduled) return
        scheduled = true
        try { worker.execute {
            while (true) {
                val version: Long
                val next: Desired
                synchronized(this) { version = revision; next = desired }
                synchronized(controlLock) {
                    if (!closed) try { apply(next) } catch (error: Exception) {
                        if (!closed) {
                            fatal = true; ramp.target = 0f
                            state = state.copy(effective = "muted", handoff = false, reason = "control_failed")
                        }
                        if (error is InterruptedException) Thread.currentThread().interrupt()
                    }
                }
                synchronized(this) {
                    if (closed || revision == version) { scheduled = false; return@execute }
                }
            }
        } } catch (_: RejectedExecutionException) { scheduled = false }
    }
    private fun inspect(): String? {
        if (control != null) return null
        val selected = unit ?: return "no_playback_master"
        if (!selected.mute) return "mute_unavailable"
        val candidate = UsbFeatureVolumeControl(transfer, selected)
        val bounds = candidate.range() ?: return "range_unavailable"
        val original = candidate.readVolume() ?: return "readback_failed"
        val muted = candidate.readMute() ?: return "mute_unavailable"
        if (original !in bounds.minimum..bounds.maximum || original > 0) return "unsafe_range"
        if (muted) return "device_muted"
        control = candidate; range = bounds; originalDb = original; currentDb = original; originalMute = muted
        return null
    }
    private fun gain(value: Desired, db: Int?): Float {
        val reference = originalDb
        val compensation = if (reference != null && db != null) 10.0.pow((reference - db) / (256.0 * 20.0)) else 1.0
        return (value.percent / 100.0 * compensation).toFloat().coerceIn(0f, 1f) // Never digitally boost.
    }
    private fun apply(value: Desired) {
        if (fatal) return
        val software = value.mode == "software"
        if (software && control == null) {
            ramp.target = value.percent / 100f
            state = UsbDacVolumeState(requested = value.mode)
            return
        }
        if (!playing) {
            ramp.target = gain(value, currentDb)
            state = state.copy(requested = value.mode, handoff = false, reason = "waiting_for_playback")
            return
        }
        val unavailable = if (!software && !failedHardware) inspect() else null
        if (unavailable != null || failedHardware) {
            ramp.target = gain(value, currentDb)
            state = UsbDacVolumeState(value.mode, "software", true, false, currentDb?.div(256f), reason = unavailable ?: failureReason ?: "control_failed")
            return
        }
        val device = control ?: return
        val baseline = originalDb ?: return
        val observedDb = device.readVolume()
        val observedMute = device.readMute()
        if (observedDb == null || observedMute == null) {
            fatal = true; ramp.target = 0f
            state = UsbDacVolumeState(value.mode, "muted", true, false, reason = "readback_failed")
            return
        }
        if (observedDb != currentDb || observedMute != originalMute) {
            // A physical control/another owner changed the device. Stop borrowing its old baseline.
            currentDb = observedDb; originalDb = observedDb; originalMute = observedMute
            changedHardware = false; failedHardware = true; failureReason = "device_changed"
            ramp.target = value.percent / 100f
            state = UsbDacVolumeState(value.mode, "software", true, false, observedDb / 256f, reason = failureReason)
            return
        }
        val desiredDb = if (software) baseline else {
            val delta = if (value.percent == 0) -32767 else (20.0 * log10(value.percent / 100.0) * 256).roundToInt()
            range!!.quantize((baseline.toLong() + delta).coerceAtLeast(-32767).toInt()).coerceAtMost(baseline)
        }
        if (desiredDb == currentDb) {
            ramp.target = gain(value, currentDb)
            state = UsbDacVolumeState(value.mode, if (software) "software" else "hardware", true, true, currentDb?.div(256f))
            return
        }
        state = state.copy(requested = value.mode, handoff = true)
        // Fade the PCM down, allow already queued packets to drain, then verify DAC mute before changing gain.
        ramp.target = 0f
        Thread.sleep(settleMs.coerceIn(150, 3000))
        if (closed) return
        if (!playing) {
            ramp.target = gain(value, currentDb)
            state = state.copy(handoff = false, reason = "waiting_for_playback")
            return
        }
        if (!device.writeMute(true)) {
            failedHardware = true; failureReason = "mute_failed"
            // A mute request may have taken effect despite a failed readback. Restore it explicitly.
            val restored = device.writeMute(originalMute ?: false)
            if (!restored) { fatal = true; ramp.target = 0f }
            else ramp.target = gain(value, currentDb)
            state = UsbDacVolumeState(value.mode, if (fatal) "muted" else "software", true, false,
                currentDb?.div(256f), reason = "mute_failed")
            return
        }
        ramp.resetWhileMuted()
        changedHardware = true
        val verified = device.writeVolume(desiredDb)
        currentDb = if (verified) desiredDb else device.readVolume()
        if (currentDb == null || !device.writeMute(originalMute ?: false)) {
            fatal = true; ramp.target = 0f
            state = UsbDacVolumeState(value.mode, "muted", true, false, reason = "readback_failed")
            return
        }
        failedHardware = !verified
        failureReason = if (verified) null else "readback_failed"
        ramp.target = gain(value, currentDb)
        state = UsbDacVolumeState(value.mode, if (!software && verified) "hardware" else "software", true,
            verified, currentDb?.div(256f), reason = if (verified) null else "readback_failed")
    }
    override fun close() {
        closed = true; ramp.target = 0f; worker.shutdownNow()
        // Caller has stopped the USB writer. Restore borrowed device settings before releasing endpoint 0.
        synchronized(controlLock) {
            val device = control
            val baseline = originalDb
            if (changedHardware && device != null && baseline != null) {
                runCatching {
                    if (!device.writeMute(true) || !device.writeVolume(baseline) || !device.writeMute(originalMute ?: false))
                        state = state.copy(readbackVerified = false, reason = "restore_failed", handoff = false)
                }.onFailure { state = state.copy(readbackVerified = false, reason = "restore_failed", handoff = false) }
            }
        }
    }
}
