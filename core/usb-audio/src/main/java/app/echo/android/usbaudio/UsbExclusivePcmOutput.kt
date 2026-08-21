package app.echo.android.usbaudio

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class UsbExclusivePcmOutput(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val probe = UsbAudioProbe(appContext)

    fun open(spec: UsbPcmFormatSpec): UsbExclusivePcmSession {
        val device = probe.findBestDevice()
            ?: return UsbExclusivePcmSession.closed(
                UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.DeviceUnavailable,
                    message = "No USB Audio Class device connected",
                ),
            )

        if (!usbManager.hasPermission(device)) {
            return UsbExclusivePcmSession.closed(
                UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.PermissionDenied,
                    message = "USB audio permission has not been granted",
                ),
            )
        }

        val snapshot = probe.snapshot()
        val selectedFormat = UsbPcmFormatSelector.chooseClosestFormat(snapshot.descriptor, spec)
            ?: return UsbExclusivePcmSession.closed(
                UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.FormatUnavailable,
                    message = "No compatible USB PCM output format for ${spec.sampleRateHz}Hz/${spec.bitDepth}bit/${spec.channelCount}ch",
                ),
            )

        val audioInterface = device.findInterface(selectedFormat)
            ?: return UsbExclusivePcmSession.closed(
                UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.FormatUnavailable,
                    selectedFormat = selectedFormat,
                    message = "USB streaming interface ${selectedFormat.interfaceNumber}:${selectedFormat.alternateSetting} is unavailable",
                ),
            )

        val endpoint = audioInterface.findOutputEndpoint(selectedFormat)
            ?: return UsbExclusivePcmSession.closed(
                UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.FormatUnavailable,
                    selectedFormat = selectedFormat,
                    message = "USB streaming output endpoint is unavailable",
                ),
            )

        val controlInterface = device.findControlInterface(selectedFormat)
        val connection = usbManager.openDevice(device)
            ?: return UsbExclusivePcmSession.closed(
                UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.OpenFailed,
                    selectedFormat = selectedFormat,
                    message = "Unable to open USB audio device",
                ),
            )

        val opened = runCatching {
            val claimedControl = controlInterface?.takeIf { interfaceToClaim ->
                interfaceToClaim.id != audioInterface.id &&
                    connection.claimInterface(interfaceToClaim, true)
            }
            if (!connection.claimInterface(audioInterface, true)) {
                error("Unable to claim USB audio streaming interface")
            }
            if (!connection.setInterface(audioInterface)) {
                error("Unable to select USB streaming alternate setting ${selectedFormat.alternateSetting}")
            }
            if (!UsbAudioClock.setSampleRate(connection, selectedFormat, spec.sampleRateHz)) {
                error("USB audio clock rejected ${spec.sampleRateHz}Hz")
            }
            val session = UsbExclusivePcmSession(
                connection = connection,
                audioInterface = audioInterface,
                controlInterface = claimedControl,
                endpoint = endpoint,
                spec = spec,
                selectedFormat = selectedFormat,
                usbVersion = snapshot.descriptor.usbVersion,
                openResult = UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.Ready,
                    selectedFormat = selectedFormat,
                    message = "USB PCM output interface is claimed",
                ),
            )
            session.startWriter()
            session
        }

        return opened.getOrElse { error ->
            runCatching { connection.setInterfaceAlt(audioInterface.id, 0) }
            runCatching { connection.releaseInterface(audioInterface) }
            if (controlInterface != null && controlInterface.id != audioInterface.id) {
                runCatching { connection.releaseInterface(controlInterface) }
            }
            connection.close()
            UsbExclusivePcmSession.closed(
                UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.OpenFailed,
                    selectedFormat = selectedFormat,
                    message = error.message ?: "Unable to prepare USB PCM output",
                ),
            )
        }
    }

    private fun UsbDevice.findInterface(format: UsbAudioStreamingFormat): UsbInterface? =
        (0 until interfaceCount)
            .asSequence()
            .map(::getInterface)
            .firstOrNull { usbInterface ->
                usbInterface.id == format.interfaceNumber &&
                    usbInterface.alternateSetting == format.alternateSetting
            }

    private fun UsbDevice.findControlInterface(format: UsbAudioStreamingFormat): UsbInterface? {
        val number = format.acInterfaceNumber ?: return null
        return (0 until interfaceCount)
            .asSequence()
            .map(::getInterface)
            .filter { it.id == number }
            .minByOrNull { it.alternateSetting }
    }

    private fun UsbInterface.findOutputEndpoint(format: UsbAudioStreamingFormat): UsbEndpoint? =
        (0 until endpointCount)
            .asSequence()
            .map(::getEndpoint)
            .firstOrNull { endpoint ->
                endpoint.direction == UsbConstants.USB_DIR_OUT &&
                    format.endpointAddress?.let { endpoint.address == it } != false
            }
}

class UsbExclusivePcmSession internal constructor(
    private val connection: UsbDeviceConnection?,
    private val audioInterface: UsbInterface?,
    private val controlInterface: UsbInterface? = null,
    private val endpoint: UsbEndpoint?,
    private val spec: UsbPcmFormatSpec? = null,
    private val selectedFormat: UsbAudioStreamingFormat? = null,
    private val usbVersion: Int? = null,
    val openResult: UsbExclusiveOpenResult,
) : AutoCloseable {
    private val lock = Any()
    private var nativeHandle: Long = 0L
    private var closed = false
    @Volatile
    private var keepAlive = false
    @Volatile
    private var disconnected = false
    private var keepAliveThread: Thread? = null

    val state: UsbExclusiveOutputState
        get() = openResult.state

    val transport: UsbEndpointTransferType?
        get() = selectedFormat?.endpointTransferType ?: when (endpoint?.type) {
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> UsbEndpointTransferType.Isochronous
            UsbConstants.USB_ENDPOINT_XFER_BULK -> UsbEndpointTransferType.Bulk
            else -> null
        }

    fun isDisconnected(): Boolean = disconnected || closed

    val bytesPerSample: Int
        get() = UsbPcmPacker.bytesPerSample(
            bitDepth = selectedFormat?.bitResolution ?: spec?.bitDepth ?: 16,
            subslotSize = selectedFormat?.subslotSize,
        )

    internal fun startWriter() {
        val endpoint = endpoint ?: return
        val spec = spec ?: return
        val format = selectedFormat ?: return
        if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_ISOC) return
        if (!UsbIsochronousNative.available) {
            error("Native isochronous writer is unavailable")
        }
        val packetsPerSecond = UsbIsoPacketizer.packetsPerSecond(
            sampleRateHz = spec.sampleRateHz,
            channelCount = format.channelCount ?: spec.channelCount,
            bytesPerSample = bytesPerSample,
            maxPacketSize = format.maxPacketSize ?: endpoint.maxPacketSize,
            usbVersion = usbVersion,
        )
        nativeHandle = UsbIsochronousNative.create(
            fileDescriptor = connection?.fileDescriptor ?: error("USB file descriptor is unavailable"),
            endpointAddress = endpoint.address,
            maxPacketSize = UsbIsoPacketizer.maxPacketPayloadBytes(format.maxPacketSize ?: endpoint.maxPacketSize),
            sampleRateHz = spec.sampleRateHz,
            channelCount = format.channelCount ?: spec.channelCount,
            bytesPerSample = bytesPerSample,
            packetsPerSecond = packetsPerSecond,
            feedbackEndpointAddress = format.feedbackEndpointAddress ?: 0,
            feedbackMaxPacketSize = format.feedbackMaxPacketSize ?: 0,
        )
        if (nativeHandle == 0L) {
            error("Unable to start native isochronous writer")
        }
        UsbIsochronousNative.prime(nativeHandle)
    }

    fun prime() {
        synchronized(lock) {
            if (!closed && nativeHandle != 0L) UsbIsochronousNative.prime(nativeHandle)
        }
    }

    fun setKeepAlive(enabled: Boolean) {
        synchronized(lock) {
            if (closed) return
            keepAlive = enabled
            if (enabled && nativeHandle != 0L) {
                UsbIsochronousNative.prime(nativeHandle)
                startKeepAliveLocked()
            }
        }
        if (!enabled) {
            keepAliveThread?.interrupt()
        }
    }

    private fun startKeepAliveLocked() {
        val existing = keepAliveThread
        if (existing != null && existing.isAlive) return
        keepAliveThread = Thread(
            {
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        synchronized(lock) {
                            if (!keepAlive || closed || nativeHandle == 0L) return@Thread
                            UsbIsochronousNative.prime(nativeHandle)
                        }
                        Thread.sleep(KEEPALIVE_SLEEP_MS)
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            },
            "echo-usb-iso-keepalive",
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun writePcm(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): UsbPcmWriteResult = synchronized(lock) {
        if (closed) {
            return UsbPcmWriteResult(UsbExclusiveOutputState.Closed, message = "USB PCM session is closed")
        }
        val connection = connection
            ?: return UsbPcmWriteResult(openResult.state, message = openResult.message)
        val endpoint = endpoint
            ?: return UsbPcmWriteResult(UsbExclusiveOutputState.OpenFailed, message = "USB endpoint is unavailable")
        val safeOffset = offset.coerceIn(0, buffer.size)
        val safeLength = length.coerceIn(0, buffer.size - safeOffset)
        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
            val written = UsbIsochronousNative.write(nativeHandle, buffer, safeOffset, safeLength)
            return when {
                written == UsbIsochronousNative.FATAL -> {
                    disconnected = true
                    UsbPcmWriteResult(UsbExclusiveOutputState.Closed, message = "USB audio device disconnected")
                }
                written < 0 ->
                    UsbPcmWriteResult(UsbExclusiveOutputState.OpenFailed, message = "USB isochronous write failed")
                else -> UsbPcmWriteResult(UsbExclusiveOutputState.Streaming, bytesWritten = written)
            }
        }
        if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) {
            return UsbPcmWriteResult(
                state = UsbExclusiveOutputState.UnsupportedTransport,
                message = "USB audio streaming endpoint ${endpoint.type.toTransferLabel()} is not supported",
            )
        }
        val written = connection.bulkTransfer(endpoint, buffer, safeOffset, safeLength, USB_WRITE_TIMEOUT_MS)
        return if (written >= 0) {
            UsbPcmWriteResult(UsbExclusiveOutputState.Streaming, bytesWritten = written)
        } else {
            UsbPcmWriteResult(UsbExclusiveOutputState.OpenFailed, message = "USB PCM write failed")
        }
    }

    fun queuedFrames(): Long = synchronized(lock) { UsbIsochronousNative.queuedFrames(nativeHandle) }

    fun completedFrames(): Long = synchronized(lock) { UsbIsochronousNative.completedFrames(nativeHandle) }

    fun capacityFrames(): Long = synchronized(lock) { UsbIsochronousNative.capacityFrames(nativeHandle) }

    fun flush() {
        synchronized(lock) {
            if (!closed) UsbIsochronousNative.flush(nativeHandle)
        }
    }

    override fun close() {
        val thread = synchronized(lock) {
            if (closed) return
            closed = true
            keepAlive = false
            val running = keepAliveThread
            keepAliveThread = null
            UsbIsochronousNative.close(nativeHandle)
            nativeHandle = 0L
            val connection = connection
            val audioInterface = audioInterface
            val controlInterface = controlInterface
            if (connection != null) {
                if (audioInterface != null) {
                    runCatching { connection.setInterfaceAlt(audioInterface.id, 0) }
                    runCatching { connection.releaseInterface(audioInterface) }
                }
                if (controlInterface != null) {
                    runCatching { connection.releaseInterface(controlInterface) }
                }
                connection.close()
            }
            running
        }
        thread?.interrupt()
    }

    private fun Int.toTransferLabel(): String =
        when (this) {
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "isochronous"
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
            UsbConstants.USB_ENDPOINT_XFER_INT -> "interrupt"
            UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "control"
            else -> "unknown"
        }

    companion object {
        private const val USB_WRITE_TIMEOUT_MS = 20
        private const val KEEPALIVE_SLEEP_MS = 2L

        fun closed(openResult: UsbExclusiveOpenResult): UsbExclusivePcmSession =
            UsbExclusivePcmSession(
                connection = null,
                audioInterface = null,
                endpoint = null,
                spec = null,
                selectedFormat = openResult.selectedFormat,
                usbVersion = null,
                openResult = openResult,
            )
    }
}

internal fun UsbDeviceConnection.setInterfaceAlt(interfaceNumber: Int, alternateSetting: Int): Boolean =
    controlTransfer(
        USB_RECIPIENT_INTERFACE,
        USB_SET_INTERFACE,
        alternateSetting,
        interfaceNumber,
        null,
        0,
        USB_SET_INTERFACE_TIMEOUT_MS,
    ) >= 0

private const val USB_RECIPIENT_INTERFACE = 0x01
private const val USB_SET_INTERFACE = 0x0B
private const val USB_SET_INTERFACE_TIMEOUT_MS = 1_000
