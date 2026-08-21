package app.echo.android.connect

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.echo.android.model.connect.EchoLinkLanDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EchoLinkLanBrowser(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val multicastLock = wifiManager?.createMulticastLock("echo-link-mdns")?.apply {
        setReferenceCounted(false)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _devices = MutableStateFlow<List<EchoLinkLanDevice>>(emptyList())
    val devices: StateFlow<List<EchoLinkLanDevice>> = _devices.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private val resolveAttempts = mutableMapOf<String, Int>()
    private var resolving = false
    private var started = false

    fun restart() {
        val snapshot = _devices.value
        stop(clearDevices = false)
        _devices.value = snapshot
        start()
    }

    fun start() {
        if (started) return
        val manager = nsdManager ?: return
        started = true
        runCatching { multicastLock?.acquire() }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                mainHandler.post { enqueueResolve(serviceInfo) }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    _devices.update { current ->
                        EchoLinkDiscoveryPolicy.removeService(current, serviceInfo.serviceName.orEmpty())
                    }
                }
            }
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(
                EchoLinkDiscoveryPolicy.ServiceType,
                NsdManager.PROTOCOL_DNS_SD,
                listener,
            )
        }.onFailure {
            stop()
        }
    }

    fun stop(clearDevices: Boolean = true) {
        val manager = nsdManager
        val listener = discoveryListener
        discoveryListener = null
        pendingResolves.clear()
        resolveAttempts.clear()
        resolving = false
        started = false
        if (clearDevices) {
            _devices.value = emptyList()
        }
        if (manager != null && listener != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock.release()
        }
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        if (!started) return
        pendingResolves.addLast(serviceInfo)
        drainResolves()
    }

    private fun drainResolves() {
        val manager = nsdManager ?: return
        if (!started || resolving) return
        val next = pendingResolves.removeFirstOrNull() ?: return
        resolving = true
        runCatching {
            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        mainHandler.post {
                            val name = serviceInfo.serviceName.orEmpty()
                            val attempts = (resolveAttempts[name] ?: 0) + 1
                            resolveAttempts[name] = attempts
                            if (started && attempts < MaxResolveAttempts) {
                                pendingResolves.addLast(serviceInfo)
                            }
                            resolving = false
                            drainResolves()
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        resolveAttempts.remove(serviceInfo.serviceName.orEmpty())
                        val device = EchoLinkDiscoveryPolicy.deviceFromResolved(
                            serviceName = serviceInfo.serviceName.orEmpty(),
                            host = EchoLinkDiscoveryPolicy.pickHost(resolvedHosts(serviceInfo)),
                            port = serviceInfo.port,
                            txt = EchoLinkDiscoveryPolicy.decodeTxt(serviceInfo.attributes.orEmpty()),
                        )
                        mainHandler.post {
                            if (device != null) {
                                _devices.update { current ->
                                    EchoLinkDiscoveryPolicy.upsertDevice(current, device)
                                }
                            }
                            resolving = false
                            drainResolves()
                        }
                    }
                },
            )
        }.onFailure {
            resolving = false
            drainResolves()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvedHosts(serviceInfo: NsdServiceInfo): List<String> {
        val hosts = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 34) {
            serviceInfo.hostAddresses.orEmpty().mapNotNullTo(hosts) { it.hostAddress }
        }
        serviceInfo.host?.hostAddress?.let(hosts::add)
        return hosts
    }

    private companion object {
        const val MaxResolveAttempts = 3
    }
}
