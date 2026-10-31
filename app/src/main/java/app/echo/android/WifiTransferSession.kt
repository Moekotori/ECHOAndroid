package app.echo.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.echo.android.connect.EchoWifiTransferAddresses
import app.echo.android.connect.EchoWifiTransferServer
import app.echo.android.data.WifiMusicReceiver
import app.echo.android.data.WifiTransferDirectoryStore
import app.echo.android.model.connect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Foreground transfer dialog owns this lifetime, sockets and cancellation. */
internal class WifiTransferSession(context: Context, private val onScan: (Uri) -> Unit) : AutoCloseable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val directories = WifiTransferDirectoryStore(this.context)
    private val mutable = MutableStateFlow(EchoWifiTransferState())
    val state = mutable.asStateFlow()
    private var tree: Uri? = null
    private var startJob: Job? = null
    private val generation = AtomicInteger()
    private val server = AtomicReference<EchoWifiTransferServer?>()

    init { scope.launch {
        try {
            val stored = withContext(Dispatchers.IO) { directories.load() } ?: return@launch
            val uri = Uri.parse(stored)
            val name = withContext(Dispatchers.IO) { WifiMusicReceiver(this@WifiTransferSession.context, uri).directoryName() }
            if (tree == null) { tree = uri; mutable.update { it.copy(directory = name) } }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* User can select another directory if the grant expired. */ }
    } }

    fun choose(uri: Uri) {
        stop()
        scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val receiver = WifiMusicReceiver(context, uri)
                    val name = receiver.directoryName()
                    directories.save(uri.toString())
                    name
                }
                tree = uri
                mutable.value = EchoWifiTransferState(directory = name)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(error = "directory") } }
        }
    }

    fun start() {
        val destination = tree ?: return
        if (startJob?.isActive == true) return
        val version = generation.incrementAndGet()
        mutable.update { EchoWifiTransferState(phase = "starting", directory = it.directory) }
        startJob = scope.launch {
            var owned: EchoWifiTransferServer? = null
            try {
                val address = withContext(Dispatchers.IO) {
                    val host = EchoWifiTransferAddresses.ipv4(context) ?: error("no_lan")
                    val receiver = WifiMusicReceiver(context, destination)
                    receiver.directoryName()
                    receiver.recoverPendingTransfers()
                    val page = context.assets.open("echo-wifi-transfer.html").use { it.readBytes() }
                    EchoWifiTransferServer(host, page, receiver::receive,
                        onReceiving = { name -> if (generation.get() == version) mutable.update { it.copy(receiving = name) } },
                        onResult = { result -> if (generation.get() == version) mutable.update {
                            it.copy(completed = it.completed + if (result.success) 1 else 0,
                                failed = it.failed + if (result.success) 0 else 1,
                                results = (listOf(result) + it.results).take(12))
                        } },
                        onBatchFinished = { scope.launch { if (generation.get() == version) onScan(destination) } },
                    ).also { owned = it }.start()
                }
                ensureActive()
                if (generation.get() != version) return@launch
                server.set(owned)
                mutable.update { it.copy(phase = "running", address = address, pairingCode = owned?.pairingCode, error = null) }
                delay(EchoWifiTransferPolicy.SessionDurationMs)
                if (generation.get() == version) {
                    stop()
                    mutable.update { it.copy(phase = "expired") }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation.get() == version) mutable.update { it.copy(phase = "idle", error = when (e.message) { "no_lan" -> "no_lan"; "cleanup_pending" -> "cleanup"; else -> "start" }) }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { owned?.close() }
            }
        }
    }

    fun scan() { tree?.let(onScan) }
    fun stop() {
        generation.incrementAndGet()
        startJob?.cancel(); startJob = null
        server.getAndSet(null)?.close()
        mutable.update { it.copy(phase = "idle", address = null, pairingCode = null, receiving = null) }
    }
    override fun close() { stop(); scope.cancel() }
}
