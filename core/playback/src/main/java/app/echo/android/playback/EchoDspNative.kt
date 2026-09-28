package app.echo.android.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One JNI call per buffer. The Kotlin processors stay in place when this library
 * is not loaded, including JVM unit tests.
 */
internal object EchoDspNative {
    val available: Boolean = runCatching { System.loadLibrary("echo_dsp") }.isSuccess
    private var linkFailed = false

    fun processEq(
        input: ByteBuffer,
        output: ByteBuffer,
        frames: Int,
        channels: Int,
        preamp: Float,
        clamp: Boolean,
        coeffs: FloatArray,
        delay: FloatArray,
    ): Boolean {
        if (!available || linkFailed || !input.isDirect || !output.isDirect) return false
        if (input.order() != ByteOrder.nativeOrder() || output.order() != ByteOrder.nativeOrder()) return false
        return call { nativeProcessEq(
            input,
            input.position(),
            output,
            output.position(),
            frames,
            channels,
            preamp,
            clamp,
            coeffs,
            delay,
        ) }
    }

    fun processKernel(
        samples: ByteBuffer,
        frames: Int,
        channels: Int,
        state: FloatArray,
        cursor: IntArray,
        delayedLeft: FloatArray,
        delayedRight: FloatArray,
        limiting: Boolean,
    ): Boolean {
        if (!available || linkFailed || !samples.isDirect || samples.order() != ByteOrder.nativeOrder()) return false
        return call { nativeProcessKernel(
            samples,
            samples.position(),
            frames,
            channels,
            state,
            cursor,
            delayedLeft,
            delayedRight,
            limiting,
        ) }
    }

    private inline fun call(block: () -> Boolean): Boolean =
        try {
            block()
        } catch (_: UnsatisfiedLinkError) {
            linkFailed = true
            false
        }

    @JvmStatic
    private external fun nativeProcessEq(
        input: ByteBuffer,
        inputOffset: Int,
        output: ByteBuffer,
        outputOffset: Int,
        frames: Int,
        channels: Int,
        preamp: Float,
        clamp: Boolean,
        coeffs: FloatArray,
        delay: FloatArray,
    ): Boolean

    @JvmStatic
    private external fun nativeProcessKernel(
        samples: ByteBuffer,
        byteOffset: Int,
        frames: Int,
        channels: Int,
        state: FloatArray,
        cursor: IntArray,
        delayedLeft: FloatArray,
        delayedRight: FloatArray,
        limiting: Boolean,
    ): Boolean
}
