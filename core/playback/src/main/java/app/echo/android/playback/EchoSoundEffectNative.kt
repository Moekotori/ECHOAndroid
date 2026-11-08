package app.echo.android.playback

import java.nio.ByteBuffer

internal interface EchoSoundEffectBackend {
    fun create(preset: String, rate: Int, channels: Int): Long
    fun process(handle: Long, samples: ByteBuffer, frames: Int, channels: Int, enabled: Boolean, metadata: IntArray): Boolean
    fun reset(handle: Long)
    fun destroy(handle: Long)
}

internal object EchoSoundEffectNative {
    @JvmStatic external fun create(preset: String, rate: Int, channels: Int): Long
    @JvmStatic external fun process(handle: Long, samples: ByteBuffer, offset: Int, frames: Int, channels: Int, enabled: Boolean, metadata: IntArray): Boolean
    @JvmStatic external fun reset(handle: Long)
    @JvmStatic external fun destroy(handle: Long)
}

internal object EchoSoundEffectJniBackend : EchoSoundEffectBackend {
    override fun create(preset: String, rate: Int, channels: Int): Long =
        if (!EchoDspNative.available) 0 else try { EchoSoundEffectNative.create(preset, rate, channels) } catch (_: LinkageError) { 0 }
    override fun process(handle: Long, samples: ByteBuffer, frames: Int, channels: Int, enabled: Boolean, metadata: IntArray): Boolean =
        EchoSoundEffectNative.process(handle, samples, samples.position(), frames, channels, enabled, metadata)
    override fun reset(handle: Long) = EchoSoundEffectNative.reset(handle)
    override fun destroy(handle: Long) = EchoSoundEffectNative.destroy(handle)
}
