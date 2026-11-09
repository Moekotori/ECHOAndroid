package app.echo.android.usbaudio

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class UsbAudioProbe(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val descriptorParser = UsbAudioDescriptorParser()

    fun findBestDevice(): UsbDevice? =
        usbManager.deviceList.values
            .filter { it.hasUsbAudioOutput() }
            .maxWithOrNull(
                compareBy<UsbDevice> { if (usbManager.hasPermission(it)) 1 else 0 }
                    .thenBy { it.interfaceCount }
                    .thenByDescending { it.deviceName },
            )

    fun snapshot(): UsbAudioDeviceSnapshot = snapshotForDevice(findBestDevice())

    fun snapshotForDevice(device: UsbDevice?, permissionPendingDeviceName: String? = null): UsbAudioDeviceSnapshot {
        if (device == null) return UsbAudioDeviceSnapshot()
        val permissionGranted = usbManager.hasPermission(device)
        val pending = device.deviceName == permissionPendingDeviceName && !permissionGranted
        val descriptorResult = if (permissionGranted) readDescriptorInfo(device) else null

        return UsbAudioDeviceSnapshot(
            connected = true,
            deviceName = device.deviceName,
            displayName = device.getDisplayName(),
            vendorId = device.vendorId,
            productId = device.productId,
            permissionGranted = permissionGranted,
            permissionPending = pending,
            rawDescriptorAvailable = descriptorResult?.getOrNull() != null,
            descriptor = descriptorResult?.getOrNull() ?: UsbAudioDescriptorInfo(),
            descriptorError = descriptorResult?.exceptionOrNull()?.message,
        )
    }

    private fun readDescriptorInfo(device: UsbDevice): Result<UsbAudioDescriptorInfo> =
        runCatching {
            val connection = usbManager.openDevice(device)
                ?: error("Unable to open USB audio device")
            try {
                descriptorParser.parse(connection.rawDescriptors ?: ByteArray(0)).copy(
                    busPacketsPerSecond = UsbIsochronousNative.busPacketsPerSecond(connection.fileDescriptor),
                )
            } finally {
                connection.close()
            }
        }

    private fun UsbDevice.hasUsbAudioOutput(): Boolean = (0 until interfaceCount).any { index ->
        val usbInterface = getInterface(index)
        usbInterface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
            usbInterface.interfaceSubclass == AUDIO_STREAMING_SUBCLASS &&
            (0 until usbInterface.endpointCount).any { endpointIndex ->
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                endpoint.direction == UsbConstants.USB_DIR_OUT &&
                    ((endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                        endpoint.attributes and USB_ENDPOINT_USAGE_MASK != USB_ENDPOINT_USAGE_FEEDBACK) ||
                        endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK)
            }
    }

    private fun UsbDevice.getDisplayName(): String =
        productName?.takeIf { it.isNotBlank() }
            ?: manufacturerName?.takeIf { it.isNotBlank() }
            ?: deviceName

    private companion object {
        const val AUDIO_STREAMING_SUBCLASS = 2
        const val USB_ENDPOINT_USAGE_MASK = 0x30
        const val USB_ENDPOINT_USAGE_FEEDBACK = 0x10
    }
}
