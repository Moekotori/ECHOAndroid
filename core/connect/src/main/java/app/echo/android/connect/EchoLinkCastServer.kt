package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteStreamItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

fun interface EchoLinkCastBodyFactory {
    fun open(uri: String, startByte: Long): EchoLinkCastBody?
}

data class EchoLinkCastBody(
    val stream: InputStream,
    val totalLength: Long?,
    val mimeType: String,
) : Closeable {
    override fun close() {
        runCatching { stream.close() }
    }
}

data class EchoLinkCastPublication(
    val token: String,
    val trackId: String,
    val uri: String,
    val mimeType: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
)

class EchoLinkCastServer(
    private val openBody: EchoLinkCastBodyFactory,
    private val allowedPeerHost: () -> String? = { null },
    private val bindHost: String = "0.0.0.0",
    private val bindPort: Int = 0,
) {
    private val publications = ConcurrentHashMap<String, EchoLinkCastPublication>()
    private val running = AtomicBoolean(false)
    private val port = AtomicInteger(0)
    private val serverSocket = AtomicReference<ServerSocket?>(null)
    private val acceptThread = AtomicReference<Thread?>(null)
    private val workers = AtomicReference(newWorkers())
    private val connections = Semaphore(EchoLinkCastPolicy.MaxConnections)
    private val lastActivityMs = AtomicLong(0L)

    val isRunning: Boolean
        get() = running.get()

    val localPort: Int
        get() = port.get()

    @Synchronized
    fun start(): Int {
        if (running.get()) return port.get()
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.soTimeout = 0
        socket.bind(InetSocketAddress(bindHost, bindPort))
        serverSocket.set(socket)
        port.set(socket.localPort)
        lastActivityMs.set(System.currentTimeMillis())
        running.set(true)
        workers.set(newWorkers())
        val thread = Thread({ acceptLoop(socket) }, "echo-link-cast-accept")
        thread.isDaemon = true
        acceptThread.set(thread)
        thread.start()
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        running.set(false)
        publications.clear()
        runCatching { serverSocket.getAndSet(null)?.close() }
        acceptThread.getAndSet(null)?.interrupt()
        workers.getAndSet(newWorkers()).shutdownNow()
        port.set(0)
        lastActivityMs.set(0L)
    }

    fun isIdle(nowMs: Long, timeoutMs: Long = EchoLinkCastPolicy.IdleTimeoutMs): Boolean {
        if (!running.get()) return false
        val last = lastActivityMs.get()
        if (last <= 0L) return false
        return nowMs - last >= timeoutMs
    }

    fun publish(items: List<EchoLinkCastPublication>, host: String, boundPort: Int): List<EchoRemoteStreamItem> {
        val next = LinkedHashMap<String, EchoLinkCastPublication>()
        items.forEach { item ->
            if (EchoLinkCastPolicy.isCastToken(item.token)) next[item.token] = item
        }
        publications.keys.retainAll(next.keys)
        publications.putAll(next)
        return next.values.map { item ->
            EchoRemoteStreamItem(
                id = item.trackId,
                streamUrl = EchoLinkCastPolicy.streamUrl(host, boundPort, item.token),
                title = item.title,
                artist = item.artist,
                album = item.album,
                artworkUrl = item.artworkUrl,
                durationMs = item.durationMs,
            )
        }
    }

    fun clear() {
        publications.clear()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get() && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (_: Throwable) {
                if (!running.get()) break else continue
            }
            val acquired = connections.tryAcquire()
            if (!acquired) {
                runCatching {
                    client.soTimeout = 2_000
                    reply(client.getOutputStream(), 503, "Service Unavailable", 0)
                    client.close()
                }
                continue
            }
            val pool = workers.get()
            try {
                pool.execute {
                    try {
                        handle(client)
                    } finally {
                        runCatching { client.close() }
                        connections.release()
                    }
                }
            } catch (_: Throwable) {
                connections.release()
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream())
        val request = readRequest(input) ?: run {
            reply(output, 400, "Bad Request", 0)
            return
        }
        if (!EchoLinkCastPolicy.peerMatches(allowedPeerHost(), client.inetAddress?.hostAddress)) {
            reply(output, 403, "Forbidden", 0)
            return
        }
        val method = request.method
        if (method != "GET" && method != "HEAD") {
            reply(output, 405, "Method Not Allowed", 0)
            return
        }
        val token = tokenFromPath(request.path)
        val publication = token?.let(publications::get)
        if (publication == null) {
            reply(output, 404, "Not Found", 0)
            return
        }
        lastActivityMs.set(System.currentTimeMillis())
        val range = EchoLinkCastPolicy.parseRange(request.headers["range"], null)
        if (request.headers["range"] != null && range == null) {
            reply(output, 416, "Range Not Satisfiable", 0)
            return
        }
        val start = range?.first ?: 0L
        val body = openBody.open(publication.uri, start) ?: run {
            reply(
                output,
                if (range != null) 416 else 404,
                if (range != null) "Range Not Satisfiable" else "Not Found",
                0,
            )
            return
        }
        body.use { opened ->
            val totalLength = opened.totalLength
            if (totalLength != null && start >= totalLength) {
                reply(output, 416, "Range Not Satisfiable", 0, extraHeaders = rangeUnsatisfiable(totalLength))
                return
            }
            val end = range?.second ?: totalLength?.minus(1L)
            val mime = EchoLinkCastPolicy.mimeTypeForUri(publication.uri, publication.mimeType ?: opened.mimeType)
            val contentLength = when {
                end != null -> (end - start + 1L).coerceAtLeast(0L)
                totalLength != null -> (totalLength - start).coerceAtLeast(0L)
                else -> null
            }
            val extra = buildString {
                append("Accept-Ranges: bytes\r\n")
                if (range != null && end != null) {
                    val total = totalLength?.toString() ?: "*"
                    append("Content-Range: bytes $start-$end/$total\r\n")
                }
            }
            val status = if (range != null) 206 else 200
            val reason = if (range != null) "Partial Content" else "OK"
            writeStatus(output, status, reason, mime, contentLength, extra)
            if (method == "HEAD") {
                output.flush()
                return
            }
            copyBody(opened.stream, output, contentLength)
            output.flush()
        }
    }

    private fun tokenFromPath(path: String): String? {
        val normalized = path.substringBefore('?')
        if (!normalized.startsWith(EchoLinkCastPolicy.PathPrefix)) return null
        val token = normalized.removePrefix(EchoLinkCastPolicy.PathPrefix).trim('/')
        return token.takeIf(EchoLinkCastPolicy::isCastToken)
    }

    private fun readRequest(input: InputStream): CastHttpRequest? {
        val headerBytes = ByteArrayOutputStream(512)
        var blank = 0
        while (headerBytes.size() < MaxHeaderBytes) {
            val next = input.read()
            if (next < 0) return null
            headerBytes.write(next)
            when (next) {
                '\n'.code -> {
                    blank += 1
                    if (blank >= 2) break
                }
                '\r'.code -> Unit
                else -> blank = 0
            }
        }
        if (blank != 2) return null
        val text = headerBytes.toString(Charsets.US_ASCII.name())
        val lines = text.split("\r\n", "\n").filter { it.isNotEmpty() }
        val requestLine = lines.firstOrNull() ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val index = line.indexOf(':')
            if (index <= 0) return@forEach
            headers[line.substring(0, index).trim().lowercase()] = line.substring(index + 1).trim()
        }
        return CastHttpRequest(method = parts[0].uppercase(), path = parts[1], headers = headers)
    }

    private fun reply(
        output: OutputStream,
        status: Int,
        reason: String,
        contentLength: Long,
        extraHeaders: String = "",
    ) {
        writeStatus(output, status, reason, "text/plain; charset=utf-8", contentLength, extraHeaders)
        output.flush()
    }

    private fun writeStatus(
        output: OutputStream,
        status: Int,
        reason: String,
        contentType: String,
        contentLength: Long?,
        extraHeaders: String,
    ) {
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            if (contentLength != null) append("Content-Length: $contentLength\r\n")
            append("Connection: close\r\n")
            append(extraHeaders)
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
    }

    private fun copyBody(input: InputStream, output: OutputStream, limit: Long?) {
        val buffer = ByteArray(EchoLinkCastPolicy.CopyBufferBytes)
        var remaining = limit
        while (remaining == null || remaining > 0L) {
            val allowed = when (remaining) {
                null -> buffer.size
                else -> minOf(buffer.size.toLong(), remaining).toInt()
            }
            val read = input.read(buffer, 0, allowed)
            if (read <= 0) break
            output.write(buffer, 0, read)
            if (remaining != null) remaining -= read
        }
    }

    private fun rangeUnsatisfiable(totalLength: Long?): String =
        if (totalLength != null) "Content-Range: bytes */$totalLength\r\n" else ""

    private fun newWorkers() = Executors.newFixedThreadPool(EchoLinkCastPolicy.MaxConnections) { runnable ->
        Thread(runnable, "echo-link-cast-io").apply { isDaemon = true }
    }

    private data class CastHttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
    )

    private companion object {
        const val MaxHeaderBytes = 8 * 1024
    }
}
