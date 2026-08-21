package app.echo.android.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackError
import app.echo.android.usbaudio.UsbAudioDeviceSnapshot
import app.echo.android.usbaudio.UsbAudioProbe
import app.echo.android.usbaudio.UsbEndpointSyncType
import app.echo.android.usbaudio.UsbEndpointTransferType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EchoUsbExclusiveApplyPolicy {
    fun shouldReapplyAfterHostPermissionGranted(
        exclusiveEnabled: Boolean,
        previouslyGranted: Boolean,
        currentlyGranted: Boolean,
    ): Boolean = exclusiveEnabled && currentlyGranted && !previouslyGranted
}

data class EchoUsbAudioStatus(
    val exclusiveEnabled: Boolean = false,
    val deviceName: String? = null,
    val connected: Boolean = false,
    val hostPermissionGranted: Boolean = false,
    val hostPermissionPending: Boolean = false,
    val audioClass: String? = null,
    val audioInterfaceCount: Int = 0,
    val audioStreamingInterfaceCount: Int = 0,
    val hasIsochronousOut: Boolean = false,
    val hasFeedbackEndpoint: Boolean = false,
    val endpointSummary: String? = null,
    val descriptorError: String? = null,
    val supportedSampleRates: List<Int> = emptyList(),
    val bitPerfectSupported: Boolean = false,
    val bitPerfectActive: Boolean = false,
    val lastRequestedSampleRateHz: Int? = null,
    val lastRequestError: EchoPlaybackError? = null,
) {
    val outputRoute: String
        get() = when {
            bitPerfectActive && deviceName != null -> "USB DAC: $deviceName / bit-perfect"
            connected && hostPermissionGranted && deviceName != null -> "USB DAC: $deviceName / USB host authorized"
            connected && deviceName != null -> "USB DAC: $deviceName / Android mixer"
            connected -> "USB DAC / Android mixer"
            else -> "Media3 / AudioTrack"
        }
}

class EchoUsbAudioMonitor(context: Context) {
    private companion object {
        const val ACTION_USB_PERMISSION = "app.echo.android.playback.USB_PERMISSION"

        @Volatile
        var retainedExclusiveEnabled: Boolean = false
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbAudioProbe = UsbAudioProbe(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var exclusiveEnabled = retainedExclusiveEnabled
    private val _status = MutableStateFlow(scan())
    val status: StateFlow<EchoUsbAudioStatus> = _status.asStateFlow()
    private var receiverRegistered = false
    private var permissionRequestPendingDeviceName: String? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val device = intent.getParcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device?.deviceName == permissionRequestPendingDeviceName) {
                        permissionRequestPendingDeviceName = null
                    }
                    refresh()
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    permissionRequestPendingDeviceName = null
                    refresh()
                    if (exclusiveEnabled) requestUsbHostPermissionIfNeeded()
                }
            }
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, mainHandler)
        registerUsbReceiver()
        refresh()
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(usbReceiver) }
            receiverRegistered = false
        }
    }

    fun setExclusiveEnabled(enabled: Boolean) {
        if (exclusiveEnabled == enabled) return
        exclusiveEnabled = enabled
        retainedExclusiveEnabled = enabled
        if (!enabled) {
            permissionRequestPendingDeviceName = null
            clearPreferredMixerAttributes()
        } else {
            requestUsbHostPermissionIfNeeded()
        }
        refresh()
    }

    fun prepareForTrack(sampleRateHz: Int?) {
        if (!exclusiveEnabled) return
        requestUsbHostPermissionIfNeeded()
        // Exclusive playback claims the USB streaming interface itself.
        // Mixer bit-perfect attributes fight that claim and can make the
        // AudioTrack fallback lose the device, so they are not requested here.
        _status.value = scan().copy(
            lastRequestedSampleRateHz = sampleRateHz?.takeIf { it > 0 },
            lastRequestError = null,
        )
    }

    private fun refresh() {
        _status.value = scan().copy(
            lastRequestedSampleRateHz = _status.value.lastRequestedSampleRateHz,
            lastRequestError = _status.value.lastRequestError,
        )
    }

    private fun scan(): EchoUsbAudioStatus {
        val device = findUsbOutputDevice()
        val usbSnapshot = if (exclusiveEnabled) {
            usbAudioProbe.snapshot(permissionRequestPendingDeviceName)
        } else {
            val usbDevice = findUsbAudioDevice()
            UsbAudioDeviceSnapshot(
                connected = device != null || usbDevice != null,
                deviceName = usbDevice?.deviceName,
                displayName = device?.getDisplayName() ?: usbDevice?.getDisplayName(),
                permissionGranted = usbDevice?.let(usbManager::hasPermission) == true,
            )
        }
        val usbDevice = findUsbAudioDevice()
        val hostPermissionGranted = usbSnapshot.permissionGranted || usbDevice?.let(usbManager::hasPermission) == true
        val pendingDeviceName = permissionRequestPendingDeviceName
        val pendingDeviceMatches = pendingDeviceName != null &&
            (usbSnapshot.deviceName == pendingDeviceName || usbDevice?.deviceName == pendingDeviceName)
        if (hostPermissionGranted && pendingDeviceMatches) {
            permissionRequestPendingDeviceName = null
        }
        val hostPermissionPending = !hostPermissionGranted && (usbSnapshot.permissionPending || pendingDeviceMatches)
        if (device == null || !exclusiveEnabled) {
            return EchoUsbAudioStatus(
                exclusiveEnabled = exclusiveEnabled,
                deviceName = usbSnapshot.displayName ?: usbDevice?.getDisplayName() ?: device?.getDisplayName(),
                connected = usbSnapshot.connected || usbDevice != null || device != null,
                hostPermissionGranted = hostPermissionGranted,
                hostPermissionPending = hostPermissionPending,
                audioClass = usbSnapshot.audioClassLabel(),
                audioInterfaceCount = usbSnapshot.descriptor.audioInterfaceCount,
                audioStreamingInterfaceCount = usbSnapshot.descriptor.audioStreamingInterfaceCount,
                hasIsochronousOut = usbSnapshot.descriptor.hasIsochronousOut,
                hasFeedbackEndpoint = usbSnapshot.descriptor.hasFeedbackEndpoint,
                endpointSummary = usbSnapshot.endpointSummary(),
                descriptorError = usbSnapshot.descriptorError,
                supportedSampleRates = usbSnapshot.sampleRates,
            )
        }

        val supportedAttributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { audioManager.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val preferredAttributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { audioManager.getPreferredMixerAttributes(androidMusicAttributes(), device) }.getOrNull()
        } else {
            null
        }

        return EchoUsbAudioStatus(
            exclusiveEnabled = exclusiveEnabled,
            deviceName = device.getDisplayName().ifBlank {
                usbSnapshot.displayName ?: usbDevice?.getDisplayName().orEmpty()
            },
            connected = true,
            hostPermissionGranted = hostPermissionGranted,
            hostPermissionPending = hostPermissionPending,
            audioClass = usbSnapshot.audioClassLabel(),
            audioInterfaceCount = usbSnapshot.descriptor.audioInterfaceCount,
            audioStreamingInterfaceCount = usbSnapshot.descriptor.audioStreamingInterfaceCount,
            hasIsochronousOut = usbSnapshot.descriptor.hasIsochronousOut,
            hasFeedbackEndpoint = usbSnapshot.descriptor.hasFeedbackEndpoint,
            endpointSummary = usbSnapshot.endpointSummary(),
            descriptorError = usbSnapshot.descriptorError,
            supportedSampleRates = (device.sampleRates.asIterable() + usbSnapshot.sampleRates)
                .filter { it > 0 }
                .distinct()
                .sorted(),
            bitPerfectSupported = supportedAttributes.any { it.isBitPerfect() },
            bitPerfectActive = preferredAttributes?.isBitPerfect() == true,
        )
    }

    private fun clearPreferredMixerAttributes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val device = findUsbOutputDevice() ?: return
        runCatching {
            audioManager.clearPreferredMixerAttributes(androidMusicAttributes(), device)
        }
    }

    private fun registerUsbReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(appContext, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun requestUsbHostPermissionIfNeeded() {
        val device = findUsbAudioDevice() ?: return
        if (usbManager.hasPermission(device)) {
            if (permissionRequestPendingDeviceName == device.deviceName) {
                permissionRequestPendingDeviceName = null
            }
            return
        }
        if (permissionRequestPendingDeviceName == device.deviceName) return

        permissionRequestPendingDeviceName = device.deviceName
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val permissionIntent = PendingIntent.getBroadcast(appContext, 0, intent, flags)
        runCatching {
            usbManager.requestPermission(device, permissionIntent)
        }.onFailure {
            permissionRequestPendingDeviceName = null
        }
    }

    private fun findUsbOutputDevice(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink && it.isUsbAudioOutput() }
            .maxByOrNull { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_USB_DEVICE -> 3
                    AudioDeviceInfo.TYPE_USB_HEADSET -> 2
                    AudioDeviceInfo.TYPE_USB_ACCESSORY -> 1
                    else -> 0
                }
            }

    private fun findUsbAudioDevice(): UsbDevice? =
        usbManager.deviceList.values
            .filter { it.isUsbAudioDevice() }
            .maxByOrNull { it.interfaceCount }

    private fun findBitPerfectAttributes(
        device: AudioDeviceInfo,
        sampleRateHz: Int,
    ): AudioMixerAttributes? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return runCatching { audioManager.getSupportedMixerAttributes(device) }
            .getOrDefault(emptyList())
            .filter { it.isBitPerfect() }
            .firstOrNull { it.format.sampleRate == sampleRateHz }
    }

    private fun AudioDeviceInfo.isUsbAudioOutput(): Boolean =
        type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            type == AudioDeviceInfo.TYPE_USB_ACCESSORY

    private fun AudioDeviceInfo.getDisplayName(): String =
        productName?.toString()?.takeIf { it.isNotBlank() }
            ?: address.takeIf { it.isNotBlank() }
            ?: "USB audio"

    private fun UsbDevice.isUsbAudioDevice(): Boolean =
        deviceClass == UsbConstants.USB_CLASS_AUDIO ||
            (0 until interfaceCount).any { index ->
                getInterface(index).interfaceClass == UsbConstants.USB_CLASS_AUDIO
            }

    private fun UsbDevice.getDisplayName(): String =
        productName?.takeIf { it.isNotBlank() }
            ?: manufacturerName?.takeIf { it.isNotBlank() }
            ?: deviceName

    private fun UsbAudioDeviceSnapshot.audioClassLabel(): String? =
        descriptor.classVersions
            .takeIf { it.isNotEmpty() }
            ?.joinToString("/") { it.label }

    private fun UsbAudioDeviceSnapshot.endpointSummary(): String? =
        descriptor.streamingFormats
            .firstOrNull { it.isIsochronousOut }
            ?.let { format ->
                val width = format.bitResolution?.let { "${it}bit" } ?: "PCM"
                val channels = format.channelCount?.let { "${it}ch" } ?: "stream"
                val endpoint = format.endpointAddress?.let { "0x${it.toString(16)}" } ?: "OUT"
                val sync = format.endpointSyncType?.toLabel().orEmpty()
                val feedback = if (descriptor.hasFeedbackEndpoint) " + feedback" else ""
                "$width $channels ${format.endpointTransferType.toLabel()} $endpoint$sync$feedback"
            }

    private fun UsbEndpointTransferType?.toLabel(): String =
        when (this) {
            UsbEndpointTransferType.Isochronous -> "iso"
            UsbEndpointTransferType.Bulk -> "bulk"
            UsbEndpointTransferType.Interrupt -> "interrupt"
            UsbEndpointTransferType.Control -> "control"
            null -> "endpoint"
        }

    private fun UsbEndpointSyncType.toLabel(): String =
        when (this) {
            UsbEndpointSyncType.Asynchronous -> " async"
            UsbEndpointSyncType.Adaptive -> " adaptive"
            UsbEndpointSyncType.Synchronous -> " sync"
            UsbEndpointSyncType.None -> ""
        }

    private fun AudioMixerAttributes.isBitPerfect(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT

    private fun androidMusicAttributes(): android.media.AudioAttributes =
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
}

private inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name) as? T
    }

@UnstableApi
fun EchoPlaybackDiagnostics.withUsbAudioStatus(status: EchoUsbAudioStatus): EchoPlaybackDiagnostics {
    val exclusive = EchoPlaybackProcessRuntime.usbExclusiveSinkStatus
    val exclusiveStreaming = exclusive?.streaming == true
    val exclusiveRoute = exclusive
        ?.takeIf { it.streaming && status.deviceName != null }
        ?.let { "USB DAC: ${status.deviceName} / exclusive ${it.transport ?: "pcm"}" }
    return copy(
        outputRoute = exclusiveRoute ?: status.outputRoute,
        offloadActive = status.bitPerfectActive,
        usbExclusiveEnabled = status.exclusiveEnabled,
        usbConnected = status.connected,
        usbDeviceName = status.deviceName,
        usbHostPermissionGranted = status.hostPermissionGranted,
        usbHostPermissionPending = status.hostPermissionPending,
        usbAudioClass = status.audioClass,
        usbAudioInterfaceCount = status.audioInterfaceCount,
        usbAudioStreamingInterfaceCount = status.audioStreamingInterfaceCount,
        usbAudioHasIsochronousOut = status.hasIsochronousOut,
        usbAudioHasFeedbackEndpoint = status.hasFeedbackEndpoint,
        usbAudioEndpointSummary = status.endpointSummary,
        usbAudioDescriptorError = status.descriptorError,
        usbBitPerfectSupported = status.bitPerfectSupported,
        usbBitPerfectActive = status.bitPerfectActive ||
            EchoPlaybackProcessRuntime.usbExclusiveSinkStatus?.streaming == true,
        usbExclusiveStreaming = exclusiveStreaming,
        usbExclusiveTransport = exclusive?.transport,
        usbSupportedSampleRates = status.supportedSampleRates,
        usbLastRequestedSampleRateHz = status.lastRequestedSampleRateHz,
        usbLastRequestError = status.lastRequestError,
    )
}
