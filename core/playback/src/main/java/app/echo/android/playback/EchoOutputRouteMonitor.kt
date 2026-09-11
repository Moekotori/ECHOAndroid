package app.echo.android.playback

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import app.echo.android.model.playback.EchoOutputDeviceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EchoOutputRouteMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _route = MutableStateFlow(EchoOutputRoute())
    val route: StateFlow<EchoOutputRoute> = _route.asStateFlow()

    private var a2dp: BluetoothA2dp? = null
    private var profileRequested = false
    private var started = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                a2dp = proxy as? BluetoothA2dp
                refresh()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dp = null
                refresh()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        bindA2dpIfPermitted()
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        val adapter = bluetoothAdapter()
        val proxy = a2dp
        if (adapter != null && proxy != null) {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
        }
        a2dp = null
        profileRequested = false
    }

    fun refreshBluetoothPermission() {
        bindA2dpIfPermitted()
        refresh()
    }

    fun refresh() {
        _route.value = scan()
    }

    private fun scan(): EchoOutputRoute {
        val preferUsb = EchoPlaybackProcessRuntime.usbExclusiveEnabled
        val devices = outputDevices().map { device ->
            EchoOutputDeviceCandidate(
                type = device.type,
                name = device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                address = deviceAddress(device),
                isSink = device.isSink,
            )
        }
        val picked = EchoOutputRoutePolicy.pickDevice(devices, preferUsb = preferUsb)
            ?: return EchoOutputRoute()
        val kind = EchoOutputRoutePolicy.kind(picked.type)
        val codec = if (kind == EchoOutputDeviceKind.Bluetooth) bluetoothCodec(picked.address) else null
        return EchoOutputRoute(
            kind = kind.id,
            deviceName = picked.name,
            bluetoothCodec = codec,
            address = picked.address,
        )
    }

    private fun outputDevices(): List<AudioDeviceInfo> {
        if (Build.VERSION.SDK_INT >= 33) {
            val routed = runCatching {
                audioManager.getAudioDevicesForAttributes(mediaAttributes())
            }.getOrNull().orEmpty()
            if (routed.isNotEmpty()) return routed.toList()
        }
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    }

    private fun mediaAttributes(): android.media.AudioAttributes =
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private fun bindA2dpIfPermitted() {
        if (!hasBluetoothConnectPermission() || profileRequested) return
        val adapter = bluetoothAdapter() ?: return
        profileRequested = adapter.getProfileProxy(appContext, profileListener, BluetoothProfile.A2DP)
    }

    private fun bluetoothAdapter() =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private fun hasBluetoothConnectPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 31) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothCodec(address: String?): String? {
        if (!hasBluetoothConnectPermission()) return null
        val proxy = a2dp ?: return null
        val device = runCatching {
            proxy.connectedDevices.firstOrNull { connected ->
                address.isNullOrBlank() || connected.address.equals(address, ignoreCase = true)
            } ?: proxy.connectedDevices.firstOrNull()
        }.getOrNull() ?: return null
        val status = invokeMethod(proxy, "getCodecStatus", BluetoothDevice::class.java, device) ?: return null
        val config = invokeMethod(status, "getCodecConfig") ?: return null
        val type = invokeMethod(config, "getCodecType") as? Int ?: return null
        val name = invokeMethod(config, "getCodecName") as? String
        return EchoOutputRoutePolicy.bluetoothCodecName(type, name)
    }

    private fun invokeMethod(target: Any, name: String, parameterType: Class<*>? = null, argument: Any? = null): Any? =
        runCatching {
            val params = if (parameterType == null) emptyArray() else arrayOf(parameterType)
            var type: Class<*>? = target.javaClass
            while (type != null) {
                val method = type.declaredMethods.firstOrNull { candidate ->
                    candidate.name == name && candidate.parameterTypes.contentEquals(params)
                } ?: type.methods.firstOrNull { candidate ->
                    candidate.name == name && candidate.parameterTypes.contentEquals(params)
                }
                if (method != null) {
                    method.isAccessible = true
                    return@runCatching if (argument == null && parameterType == null) {
                        method.invoke(target)
                    } else {
                        method.invoke(target, argument)
                    }
                }
                type = type.superclass
            }
            null
        }.getOrNull()

    private fun deviceAddress(device: AudioDeviceInfo): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        val value = device.address.trim()
        return value.takeIf { it.isNotEmpty() }
    }
}
