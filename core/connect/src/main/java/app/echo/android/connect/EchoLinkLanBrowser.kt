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
    private var resolvingServiceName: String? = null
    private var resolving = false
    private var started = false
    private var generation = 0L
    private val lostServices = mutableSetOf<String>()

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
        val epoch = ++generation
        runCatching { multicastLock?.acquire() }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                mainHandler.post { if (epoch == generation) stop() }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    if (epoch != generation || !started) return@post
                    lostServices.remove(serviceInfo.serviceName)
                    enqueueResolve(serviceInfo)
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    if (epoch != generation || !started) return@post
                    if (serviceInfo.serviceName == resolvingServiceName) lostServices.add(serviceInfo.serviceName.orEmpty())
                    pendingResolves.removeAll { it.serviceName == serviceInfo.serviceName }
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
        generation += 1
        lostServices.clear()
        val manager = nsdManager
        val listener = discoveryListener
        discoveryListener = null
        resolvingServiceName = null
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
        if (pendingResolves.size >= 64 || pendingResolves.any { it.serviceName == serviceInfo.serviceName }) return
        pendingResolves.addLast(serviceInfo)
        drainResolves()
    }

    private fun drainResolves() {
        val manager = nsdManager ?: return
        if (!started || resolving) return
        val next = pendingResolves.removeFirstOrNull() ?: return
        resolving = true
        resolvingServiceName = next.serviceName
        val epoch = generation
        runCatching {
            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        mainHandler.post {
                            if (epoch != generation || !started) return@post
                            val name = serviceInfo.serviceName.orEmpty()
                            val attempts = (resolveAttempts[name] ?: 0) + 1
                            resolveAttempts[name] = attempts
                            if (name !in lostServices && attempts < MaxResolveAttempts) {
                                pendingResolves.addLast(serviceInfo)
                            } else {
                                resolveAttempts.remove(name)
                            }
                            lostServices.remove(serviceInfo.serviceName)
                            resolvingServiceName = null
                            resolving = false
                            drainResolves()
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val device = EchoLinkDiscoveryPolicy.deviceFromResolved(
                            serviceName = serviceInfo.serviceName.orEmpty(),
                            host = EchoLinkDiscoveryPolicy.pickHost(resolvedHosts(serviceInfo)),
                            port = serviceInfo.port,
                            txt = EchoLinkDiscoveryPolicy.decodeTxt(serviceInfo.attributes.orEmpty()),
                        )
                        mainHandler.post {
                            if (epoch != generation || !started) return@post
                            resolveAttempts.remove(serviceInfo.serviceName.orEmpty())
                            if (device != null && device.serviceName !in lostServices) {
                                _devices.update { current ->
                                    EchoLinkDiscoveryPolicy.upsertDevice(current, device)
                                }
                            }
                            lostServices.remove(serviceInfo.serviceName)
                            resolvingServiceName = null
                            resolving = false
                            drainResolves()
                        }
                    }
                },
            )
        }.onFailure {
            resolvingServiceName = null
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
