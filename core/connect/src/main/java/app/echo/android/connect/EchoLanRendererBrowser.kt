package app.echo.android.connect

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.echo.android.model.connect.EchoLanRenderer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EchoLanRendererBrowser(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val multicastLock = wifiManager?.createMulticastLock("echo-lan-renderer")?.apply {
        setReferenceCounted(false)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "echo-lan-renderer").apply { isDaemon = true }
    }
    private val _devices = MutableStateFlow<List<EchoLanRenderer>>(emptyList())
    val devices: StateFlow<List<EchoLanRenderer>> = _devices.asStateFlow()
    private val _state = MutableStateFlow(EchoLinkDiscoveryState.Idle)
    val state: StateFlow<EchoLinkDiscoveryState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)
    private var generation = 0L
    private var ssdpSocket: DatagramSocket? = null
    private var chromecastListener: NsdManager.DiscoveryListener? = null
    private var resolving = false
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private val fetchedLocations = mutableSetOf<String>()

    fun restart() {
        val snapshot = _devices.value
        stop(clearDevices = false)
        _devices.value = snapshot
        start()
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        _state.value = EchoLinkDiscoveryState.Searching
        val epoch = ++generation
        fetchedLocations.clear()
        runCatching { multicastLock?.acquire() }
        io.execute { runSsdp(epoch) }
        startChromecast(epoch)
        mainHandler.postDelayed({
            if (started.get() && epoch == generation) {
                _state.value = EchoLinkDiscoveryState.Watching
            }
        }, 8_000L)
    }

    fun stop(clearDevices: Boolean = true) {
        started.set(false)
        generation += 1
        _state.value = EchoLinkDiscoveryState.Idle
        runCatching { ssdpSocket?.close() }
        ssdpSocket = null
        val listener = chromecastListener
        chromecastListener = null
        pendingResolves.clear()
        resolving = false
        if (listener != null) {
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        if (clearDevices) _devices.value = emptyList()
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock.release()
        }
    }

    private fun runSsdp(epoch: Long) {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            soTimeout = 1_000
            bind(InetSocketAddress(0))
        }
        ssdpSocket = socket
        val group = runCatching { InetAddress.getByName(EchoLanRendererPolicy.SsdpHost) }.getOrNull()
            ?: return
        try {
            EchoLanRendererPolicy.SearchTargets.forEach { target ->
                val payload = EchoLanRendererPolicy.searchMessage(target).toByteArray(StandardCharsets.US_ASCII)
                repeat(2) {
                    runCatching {
                        socket.send(
                            DatagramPacket(
                                payload,
                                payload.size,
                                group,
                                EchoLanRendererPolicy.SsdpPort,
                            ),
                        )
                    }
                }
            }
            val buffer = ByteArray(2 * 1024)
            val deadline = System.currentTimeMillis() + 7_000L
            while (started.get() && epoch == generation && System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                val received = runCatching {
                    socket.receive(packet)
                    true
                }.getOrDefault(false)
                if (!received) continue
                val raw = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                val ad = EchoLanRendererPolicy.parseResponse(raw) ?: continue
                fetchDescription(epoch, ad)
            }
        } finally {
            runCatching { socket.close() }
            if (ssdpSocket === socket) ssdpSocket = null
        }
    }

    private fun fetchDescription(epoch: Long, ad: EchoSsdpAdvertisement) {
        val location = ad.location
        synchronized(fetchedLocations) {
            if (!fetchedLocations.add(location.lowercase())) return
            if (fetchedLocations.size > EchoLanRendererPolicy.MaxDevices * 2) return
        }
        io.execute {
            if (!started.get() || epoch != generation) return@execute
            val xml = downloadXml(location) ?: return@execute
            val renderer = EchoLanRendererPolicy.rendererFromDescription(location, xml, ad.usn)
                ?: return@execute
            val enriched = enrichSinks(renderer)
            mainHandler.post {
                if (!started.get() || epoch != generation) return@post
                _devices.update { EchoLanRendererPolicy.upsert(it, enriched) }
            }
        }
    }

    private fun downloadXml(location: String): String? {
        val connection = runCatching { URL(location).openConnection() as HttpURLConnection }.getOrNull()
            ?: return null
        return runCatching {
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }
            val bytes = connection.inputStream.use { input ->
                input.readBytes().let { data ->
                    if (data.size > EchoLanRendererPolicy.MaxDescriptionBytes) {
                        data.copyOf(EchoLanRendererPolicy.MaxDescriptionBytes)
                    } else {
                        data
                    }
                }
            }
            String(bytes, StandardCharsets.UTF_8)
        }.getOrNull().also {
            runCatching { connection.disconnect() }
        }
    }

    private fun startChromecast(epoch: Long) {
        val manager = nsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    if (!started.get() || epoch != generation) return@post
                    if (pendingResolves.size >= 16) return@post
                    if (pendingResolves.any { it.serviceName == serviceInfo.serviceName }) return@post
                    pendingResolves.addLast(serviceInfo)
                    drainChromecast(epoch)
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }
        chromecastListener = listener
        runCatching {
            manager.discoverServices(
                EchoLanRendererPolicy.ChromecastServiceType,
                NsdManager.PROTOCOL_DNS_SD,
                listener,
            )
        }
    }

    private fun drainChromecast(epoch: Long) {
        val manager = nsdManager ?: return
        if (resolving || !started.get() || epoch != generation) return
        val next = pendingResolves.removeFirstOrNull() ?: return
        resolving = true
        runCatching {
            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        mainHandler.post {
                            resolving = false
                            drainChromecast(epoch)
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = resolvedHost(serviceInfo)
                        val renderer = EchoLanRendererPolicy.chromecastFromResolved(
                            serviceName = serviceInfo.serviceName.orEmpty(),
                            host = host,
                            port = serviceInfo.port,
                            txt = EchoLinkDiscoveryPolicy.decodeTxt(serviceInfo.attributes.orEmpty()),
                        )
                        mainHandler.post {
                            if (started.get() && epoch == generation && renderer != null) {
                                _devices.update { EchoLanRendererPolicy.upsert(it, renderer) }
                            }
                            resolving = false
                            drainChromecast(epoch)
                        }
                    }
                },
            )
        }.onFailure {
            resolving = false
            drainChromecast(epoch)
        }
    }

    private fun enrichSinks(renderer: EchoLanRenderer): EchoLanRenderer {
        if (renderer.connectionManager == null) return renderer
        val sinks = runCatching { EchoDlnaClient().protocolInfoSinks(renderer) }.getOrNull().orEmpty()
        return if (sinks.isEmpty()) renderer else renderer.copy(sinkMimeTypes = sinks)
    }

    @Suppress("DEPRECATION")
    private fun resolvedHost(serviceInfo: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= 34) {
            serviceInfo.hostAddresses.orEmpty().firstNotNullOfOrNull { it.hostAddress }?.let { return it }
        }
        return serviceInfo.host?.hostAddress
    }
}
