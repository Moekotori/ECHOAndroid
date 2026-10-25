package app.echo.android.playback

import app.echo.android.usbaudio.*
import java.nio.ByteBuffer

/** Prebuffer raw samples, so a volume/mode change cannot replay PCM packed with stale gain. */
internal class EchoUsbPendingPcm(private val encoding: UsbPcmSourceEncoding, private val channels: Int,
    private val destBytes: Int, maxPackedBytes: Int) {
    private val sourceFrameBytes = UsbPcmPacker.sourceBytesPerFrame(encoding, channels)
    private val destFrameBytes = destBytes * channels
    private val bytes = ByteArray(maxPackedBytes / destFrameBytes * sourceFrameBytes)
    private val view = ByteBuffer.wrap(bytes)
    private var size = 0
    val empty: Boolean get() = size == 0
    fun stash(source: ByteBuffer): Int {
        val copied = minOf(source.remaining(), bytes.size - size) / sourceFrameBytes * sourceFrameBytes
        if (copied <= 0) return 0
        view.order(source.order())
        source.get(bytes, size, copied); size += copied
        return copied
    }
    fun pack(destination: ByteArray, volume: Float, ramp: UsbPcmGainRamp?): Int {
        view.clear(); view.limit(size)
        return UsbPcmPacker.pack(view, encoding, size / sourceFrameBytes, channels, destBytes,
            destination, volume = volume, gainRamp = ramp)
    }
    fun consume(packedBytes: Int) {
        val used = (packedBytes / destFrameBytes * sourceFrameBytes).coerceIn(0, size)
        System.arraycopy(bytes, used, bytes, 0, size - used); size -= used
    }
    fun clear() { size = 0 }
}
