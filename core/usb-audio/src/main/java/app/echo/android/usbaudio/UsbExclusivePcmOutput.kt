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
        val selectedFormat = UsbPcmFormatSelector.chooseFormat(snapshot.descriptor, spec)
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

        val connection = usbManager.openDevice(device)
            ?: return UsbExclusivePcmSession.closed(
                UsbExclusiveOpenResult(
                    state = UsbExclusiveOutputState.OpenFailed,
                    selectedFormat = selectedFormat,
                    message = "Unable to open USB audio device",
                ),
            )

        val opened = runCatching {
            if (!connection.claimInterface(audioInterface, true)) {
                error("Unable to claim USB audio streaming interface")
            }
            runCatching { connection.setInterface(audioInterface) }
            UsbAudioClock.setSampleRate(connection, selectedFormat, spec.sampleRateHz)
            val session = UsbExclusivePcmSession(
                connection = connection,
                audioInterface = audioInterface,
                endpoint = endpoint,
                spec = spec,
                selectedFormat = selectedFormat,
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
    private val endpoint: UsbEndpoint?,
    private val spec: UsbPcmFormatSpec? = null,
    private val selectedFormat: UsbAudioStreamingFormat? = null,
    val openResult: UsbExclusiveOpenResult,
) : AutoCloseable {
    private var nativeHandle: Long = 0L
    private var closed = false

    val state: UsbExclusiveOutputState
        get() = openResult.state

    val transport: UsbEndpointTransferType?
        get() = selectedFormat?.endpointTransferType ?: when (endpoint?.type) {
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> UsbEndpointTransferType.Isochronous
            UsbConstants.USB_ENDPOINT_XFER_BULK -> UsbEndpointTransferType.Bulk
            else -> null
        }

    val bytesPerSample: Int
        get() = UsbPcmPacker.bytesPerSample(
            bitDepth = spec?.bitDepth ?: selectedFormat?.bitResolution ?: 16,
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
            channelCount = spec.channelCount,
            bytesPerSample = bytesPerSample,
            maxPacketSize = format.maxPacketSize ?: endpoint.maxPacketSize,
        )
        nativeHandle = UsbIsochronousNative.create(
            fileDescriptor = connection?.fileDescriptor ?: error("USB file descriptor is unavailable"),
            endpointAddress = endpoint.address,
            maxPacketSize = UsbIsoPacketizer.maxPacketPayloadBytes(format.maxPacketSize ?: endpoint.maxPacketSize),
            sampleRateHz = spec.sampleRateHz,
            channelCount = spec.channelCount,
            bytesPerSample = bytesPerSample,
            packetsPerSecond = packetsPerSecond,
        )
        if (nativeHandle == 0L) {
            error("Unable to start native isochronous writer")
        }
    }

    fun writePcm(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): UsbPcmWriteResult {
        val connection = connection
            ?: return UsbPcmWriteResult(openResult.state, message = openResult.message)
        val endpoint = endpoint
            ?: return UsbPcmWriteResult(UsbExclusiveOutputState.OpenFailed, message = "USB endpoint is unavailable")
        val safeOffset = offset.coerceIn(0, buffer.size)
        val safeLength = length.coerceIn(0, buffer.size - safeOffset)
        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
            val written = UsbIsochronousNative.write(nativeHandle, buffer, safeOffset, safeLength)
            return when {
                written < 0 -> UsbPcmWriteResult(UsbExclusiveOutputState.OpenFailed, message = "USB isochronous write failed")
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

    fun queuedFrames(): Long = UsbIsochronousNative.queuedFrames(nativeHandle)

    fun completedFrames(): Long = UsbIsochronousNative.completedFrames(nativeHandle)

    override fun close() {
        if (closed) return
        closed = true
        UsbIsochronousNative.close(nativeHandle)
        nativeHandle = 0L
        val connection = connection ?: return
        val audioInterface = audioInterface
        if (audioInterface != null) {
            runCatching { connection.releaseInterface(audioInterface) }
        }
        connection.close()
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

        fun closed(openResult: UsbExclusiveOpenResult): UsbExclusivePcmSession =
            UsbExclusivePcmSession(
                connection = null,
                audioInterface = null,
                endpoint = null,
                spec = null,
                selectedFormat = openResult.selectedFormat,
                openResult = openResult,
            )
    }
}
