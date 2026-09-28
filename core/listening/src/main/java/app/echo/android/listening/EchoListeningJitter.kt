package app.echo.android.listening

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
    private var epoch = 0L
    private val queued = HashMap<Int, EchoListeningPacket>(maxAhead)
    private var nextSequence = 0
    private var primed = false
    private var gapSinceMs: Long? = null

    @Synchronized
    fun reset(epoch: Long) {
        this.epoch = epoch
        queued.clear()
        primed = false
        gapSinceMs = null
    }

    @Synchronized
    fun push(packet: EchoListeningPacket): Boolean {
        if (packet.epoch != epoch) return false
        val sequence = packet.sequence
        if (primed) {
            if (Integer.compareUnsigned(sequence, nextSequence) < 0) return false
            if (unsignedDistance(nextSequence, sequence) > maxAhead) return false
        }
        if (queued.size >= maxAhead && !queued.containsKey(sequence)) {
            val anchor = if (primed) nextSequence else lowestSequence() ?: sequence
            val furthest = queued.keys.maxWith(unsignedFrom(anchor))
            if (unsignedDistance(anchor, sequence) >= unsignedDistance(anchor, furthest)) return false
            queued.remove(furthest)
        }
        queued[sequence] = packet
        return true
    }

    @Synchronized
    fun poll(nowMs: Long): EchoListeningOutput? {
        if (!primed) {
            if (queued.size < targetPackets) return null
            primed = true
            nextSequence = queued.keys.minWith(UNSIGNED)
            gapSinceMs = null
        }
        val ready = queued.remove(nextSequence)
        if (ready != null) {
            gapSinceMs = null
            nextSequence += 1
            return EchoListeningOutput.Packet(ready)
        }
        if (queued.isEmpty()) return null
        val waited = gapSinceMs
        if (waited == null) {
            gapSinceMs = nowMs
            return null
        }
        if (nowMs - waited < gapWaitMs) return null
        gapSinceMs = null
        nextSequence += 1
        return EchoListeningOutput.Gap
    }

    private fun lowestSequence(): Int? =
        queued.keys.minWithOrNull(UNSIGNED)

    private fun unsignedFrom(origin: Int): Comparator<Int> =
        Comparator { left, right ->
            java.lang.Integer.compare(unsignedDistance(origin, left), unsignedDistance(origin, right))
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
