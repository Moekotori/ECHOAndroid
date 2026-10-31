package app.echo.android.listening

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed interface EchoListeningOutput {
    data class Packet(val packet: EchoListeningPacket) : EchoListeningOutput
    data object Gap : EchoListeningOutput
}

/**
 * Orders 20 ms Opus frames for one programme epoch.
 * Playback starts after [targetPackets]. Packets older than the cursor, from
 * another epoch, or more than [maxAhead] frames ahead are dropped.
 */
class EchoListeningJitter(
    private val targetPackets: Int = 20,
    private val maxAhead: Int = 50,
    private val gapWaitMs: Long = 40,
) {
    private val lock = ReentrantLock()
    private val packetSignal = lock.newCondition()
    private var epoch = 0L
    private val queued = HashMap<Int, EchoListeningPacket>(maxAhead)
    private var nextSequence = 0
    private var primed = false
    private var gapSinceMs: Long? = null

    fun reset(epoch: Long) {
        lock.withLock {
            this.epoch = epoch
            queued.clear()
            primed = false
            gapSinceMs = null
            packetSignal.signalAll()
        }
    }

    fun push(packet: EchoListeningPacket): Boolean = lock.withLock {
        if (packet.epoch != epoch) return@withLock false
        val sequence = packet.sequence
        if (primed) {
            if (Integer.compareUnsigned(sequence, nextSequence) < 0) return@withLock false
            if (unsignedDistance(nextSequence, sequence) > maxAhead) return@withLock false
        }
        if (queued.size >= maxAhead && !queued.containsKey(sequence)) {
            val anchor = if (primed) nextSequence else lowestSequence() ?: sequence
            val furthest = queued.keys.maxWith(unsignedFrom(anchor))
            if (unsignedDistance(anchor, sequence) >= unsignedDistance(anchor, furthest)) return@withLock false
            queued.remove(furthest)
        }
        queued[sequence] = packet
        packetSignal.signalAll()
        true
    }

    fun poll(nowMs: Long): EchoListeningOutput? = lock.withLock {
        if (!primed) {
            if (queued.size < targetPackets) return@withLock null
            primed = true
            nextSequence = queued.keys.minWith(UNSIGNED)
            gapSinceMs = null
        }
        val ready = queued.remove(nextSequence)
        if (ready != null) {
            gapSinceMs = null
            nextSequence += 1
            return@withLock EchoListeningOutput.Packet(ready)
        }
        if (queued.isEmpty()) return@withLock null
        val waited = gapSinceMs
        if (waited == null) {
            gapSinceMs = nowMs
            return@withLock null
        }
        if (nowMs - waited < gapWaitMs) return@withLock null
        gapSinceMs = null
        nextSequence += 1
        EchoListeningOutput.Gap
    }

    /** Blocks until a packet/reset arrives or the audio thread should re-check its deadline. */
    fun awaitPacket(timeoutMs: Long) {
        if (timeoutMs <= 0L) return
        lock.lock()
        try {
            packetSignal.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            lock.unlock()
        }
    }

    private fun lowestSequence(): Int? =
        queued.keys.minWithOrNull(UNSIGNED)

    private fun unsignedFrom(origin: Int): Comparator<Int> =
        Comparator { left, right ->
            Integer.compare(unsignedDistance(origin, left), unsignedDistance(origin, right))
        }

    companion object {
        private val UNSIGNED: Comparator<Int> = Comparator { left, right ->
            Integer.compareUnsigned(left, right)
        }

        internal fun unsignedDistance(from: Int, to: Int): Int {
            val delta = to.toLong() - from.toLong()
            val wrapped = if (delta >= 0) delta else delta + 0x1_0000_0000L
            return if (wrapped > Int.MAX_VALUE) Int.MAX_VALUE else wrapped.toInt()
        }
    }
}
