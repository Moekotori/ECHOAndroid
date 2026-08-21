package app.echo.android.usbaudio

internal object UsbIsochronousNative {
    const val FATAL = -2

    val available: Boolean = runCatching { System.loadLibrary("echo_usb_isoc") }.isSuccess

    fun create(
        fileDescriptor: Int,
        endpointAddress: Int,
        maxPacketSize: Int,
        sampleRateHz: Int,
        channelCount: Int,
        bytesPerSample: Int,
        packetsPerSecond: Int,
        feedbackEndpointAddress: Int = 0,
        feedbackMaxPacketSize: Int = 0,
    ): Long {
        if (!available || fileDescriptor < 0) return 0L
        return nativeCreate(
            fileDescriptor,
            endpointAddress,
            maxPacketSize,
            sampleRateHz,
            channelCount,
            bytesPerSample,
            packetsPerSecond,
            feedbackEndpointAddress,
            feedbackMaxPacketSize,
        )
    }

    fun write(handle: Long, packed: ByteArray, offset: Int, length: Int): Int {
        if (handle == 0L || length <= 0) return 0
        return nativeWrite(handle, packed, offset, length)
    }

    fun completedFrames(handle: Long): Long = if (handle == 0L) 0L else nativeCompletedFrames(handle)

    fun queuedFrames(handle: Long): Long = if (handle == 0L) 0L else nativeQueuedFrames(handle)

    fun capacityFrames(handle: Long): Long = if (handle == 0L) 1L else nativeCapacityFrames(handle)

    fun prime(handle: Long) {
        if (handle != 0L) nativePrime(handle)
    }

    fun flush(handle: Long) {
        if (handle != 0L) nativeFlush(handle)
    }

    fun close(handle: Long) {
        if (handle != 0L) nativeClose(handle)
    }

    @JvmStatic
    private external fun nativeCreate(
        fd: Int,
        endpointAddress: Int,
        maxPacketSize: Int,
        sampleRateHz: Int,
        channelCount: Int,
        bytesPerSample: Int,
        packetsPerSecond: Int,
        feedbackEndpointAddress: Int,
        feedbackMaxPacketSize: Int,
    ): Long

    @JvmStatic
    private external fun nativeWrite(handle: Long, packed: ByteArray, offset: Int, length: Int): Int

    @JvmStatic
    private external fun nativeCompletedFrames(handle: Long): Long

    @JvmStatic
    private external fun nativeQueuedFrames(handle: Long): Long

    @JvmStatic
    private external fun nativeCapacityFrames(handle: Long): Long

    @JvmStatic
    private external fun nativePrime(handle: Long)

    @JvmStatic
    private external fun nativeFlush(handle: Long)

    @JvmStatic
    private external fun nativeClose(handle: Long)
}
