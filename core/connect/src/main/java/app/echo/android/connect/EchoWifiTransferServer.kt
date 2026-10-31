package app.echo.android.connect

import app.echo.android.model.connect.EchoWifiTransferPolicy
import app.echo.android.model.connect.EchoWifiTransferResult
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** Separate upload-only session: no Echo Link routes, media tokens or library exposure. */
class EchoWifiTransferServer(
    private val host: String,
    private val page: ByteArray,
    private val receive: (String, Long, InputStream) -> String,
    private val onReceiving: (String?) -> Unit = {},
    private val onResult: (EchoWifiTransferResult) -> Unit = {},
    private val onBatchFinished: () -> Unit = {},
) : Closeable {
    private val running = AtomicBoolean(false)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val permits = Semaphore(3)
    private val upload = Semaphore(1)
    private val workers = Executors.newFixedThreadPool(3) { r -> Thread(r, "echo-wifi-upload").apply { isDaemon = true } }
    private var socket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var files = 0
    private var bytes = 0L
    private var needsScan = false
    private val startedAt = System.nanoTime()
    private val token = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    val pairingCode = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
    private val pairingFailures = java.util.concurrent.atomic.AtomicInteger(0)
    private val port get() = socket?.localPort ?: 0
    val address get() = "http://$host:$port/"

    @Synchronized fun start(): String {
        check(socket == null && !workers.isShutdown)
        val bound = ServerSocket()
        try { bound.bind(InetSocketAddress(host, 0)); bound.soTimeout = 15_000 }
        catch (error: IOException) { bound.close(); throw error }
        socket = bound
        running.set(true)
        acceptThread = Thread({ accept(bound) }, "echo-wifi-accept").apply { isDaemon = true; start() }
        return address
    }

    private fun accept(server: ServerSocket) {
        while (running.get()) {
            if (expired()) { close(); break }
            val client = try { server.accept() }
            catch (_: SocketTimeoutException) { continue }
            catch (_: IOException) { break }
            if (!permits.tryAcquire()) { client.close(); continue }
            clients.add(client)
            try { workers.execute {
                try { handle(client) }
                catch (_: IOException) { /* Disconnect/close cancels the body and receiver cleanup. */ }
                catch (_: IllegalArgumentException) { runCatching { reply(client, 400, "bad_request") } }
                finally { clients.remove(client); runCatching { client.close() }; permits.release() }
            } } catch (_: java.util.concurrent.RejectedExecutionException) {
                clients.remove(client); client.close(); permits.release()
            }
        }
    }

    private fun expired() = (System.nanoTime() - startedAt) / 1_000_000 >= EchoWifiTransferPolicy.SessionDurationMs

    private fun handle(client: Socket) {
        if (!running.get() || expired()) return
        if (!client.inetAddress.isSiteLocalAddress && !client.inetAddress.isLinkLocalAddress && !client.inetAddress.isLoopbackAddress) {
            reply(client, 403, "lan_only"); return
        }
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream(), 16 * 1024)
        var headerBytes = 0
        fun line(): String {
            val result = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0) throw EOFException()
                headerBytes++
                require(headerBytes <= 16 * 1024 && result.size() <= 4096)
                if (value == 10) break
                require(value == 13 || value in 32..126)
                result.write(value)
            }
            val raw = result.toString("US-ASCII")
            require(raw.endsWith('\r'))
            return raw.dropLast(1)
        }
        val request = line().split(' ')
        require(request.size == 3 && request[2] == "HTTP/1.1" && request[1].startsWith('/'))
        val headers = HashMap<String, String>()
        var count = 0
        while (true) {
            val entry = line()
            if (entry.isEmpty()) break
            require(++count <= 48)
            val colon = entry.indexOf(':')
            require(colon > 0)
            val key = entry.take(colon).lowercase(java.util.Locale.ROOT)
            require(key.all { c -> c.isLetterOrDigit() || c == '-' } && !headers.containsKey(key))
            headers[key] = entry.substring(colon + 1).trim()
        }
        val authority = "$host:$port"
        if (headers["host"] != authority || headers["transfer-encoding"] != null) {
            reply(client, 400, "bad_request"); return
        }
        if (request[0] == "GET" && request[1] == "/") {
            replyBody(client, 200, "text/html; charset=utf-8", page); return
        }
        if (headers["origin"]?.let { it != "http://$authority" } == true) {
            reply(client, 403, "session_invalid"); return
        }
        if (request[0] == "POST" && request[1] == "/pair" && headers["content-length"] == "0") {
            if (pairingFailures.get() >= 8) { reply(client, 429, "pairing_limited"); return }
            if (!MessageDigest.isEqual(pairingCode.toByteArray(), headers["x-echo-code"].orEmpty().toByteArray())) {
                pairingFailures.incrementAndGet(); reply(client, 403, "code_invalid"); return
            }
            replyBody(client, 200, "application/json; charset=utf-8", JSONObject().put("token", token).toString().toByteArray())
            return
        }
        if (!MessageDigest.isEqual(token.toByteArray(Charsets.US_ASCII),
                headers["x-echo-token"].orEmpty().toByteArray(Charsets.US_ASCII)) ||
            headers["origin"]?.let { it != "http://$authority" } == true) {
            reply(client, 403, "session_invalid"); return
        }
        if (headers["expect"] != null) { reply(client, 417, "bad_request"); return }
        val length = headers["content-length"]?.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
        if (request[0] == "POST" && request[1] == "/finish" && length == 0L) {
            if (!upload.tryAcquire()) { reply(client, 409, "busy"); return }
            try {
                if (needsScan) { needsScan = false; onBatchFinished() }
                reply(client, 200, "finished")
            } finally { upload.release() }
            return
        }
        if (request[0] != "POST" || !request[1].startsWith("/upload?name=")) {
            reply(client, 404, "not_found"); return
        }
        if (length == null || length <= 0 || length > EchoWifiTransferPolicy.MaxFileBytes) {
            reply(client, 413, "file_limit"); return
        }
        val name = URLDecoder.decode(request[1].substringAfter("/upload?name="), "UTF-8")
        EchoWifiTransferPolicy.pathSegments(name)
        if (!upload.tryAcquire()) { reply(client, 409, "busy"); return }
        try {
            if (files >= EchoWifiTransferPolicy.MaxFiles || bytes > EchoWifiTransferPolicy.MaxSessionBytes - length) {
                reply(client, 413, "session_limit"); return
            }
            files++; bytes += length
            onReceiving(name)
            try {
                val body = object : FilterInputStream(input) {
                    private fun checkActive() { if (!running.get() || Thread.currentThread().isInterrupted) throw InterruptedIOException("cancelled") }
                    override fun read(): Int { checkActive(); return input.read().also { checkActive() } }
                    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                        checkActive(); return input.read(buffer, offset, count).also { checkActive() }
                    }
                }
                val savedName = receive(name, length, body)
                needsScan = true
                onResult(EchoWifiTransferResult(savedName, true))
                runCatching { reply(client, 200, "saved") }
            } catch (error: Exception) {
                val reason = when {
                    error.message == "cleanup_pending" -> "cleanup_pending"
                    error is IllegalArgumentException -> if (error.message == "name_conflict") "name_conflict" else "unsupported_file"
                    error is SecurityException -> "permission_lost"
                    error is InterruptedIOException || error is SocketException -> "cancelled"
                    else -> "write_failed"
                }
                onResult(EchoWifiTransferResult(name, false, reason))
                runCatching { reply(client, 422, reason) }
            } finally { onReceiving(null) }
        } finally { upload.release() }
    }

    private fun reply(client: Socket, status: Int, reason: String) =
        replyBody(client, status, "application/json; charset=utf-8", JSONObject().put("ok", status == 200).put("reason", reason).toString().toByteArray())

    private fun replyBody(client: Socket, status: Int, type: String, body: ByteArray) {
        val output = BufferedOutputStream(client.getOutputStream())
        output.write(("HTTP/1.1 $status Response\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Connection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'\r\n\r\n").toByteArray(Charsets.US_ASCII))
        output.write(body); output.flush()
    }

    @Synchronized override fun close() {
        running.set(false)
        runCatching { socket?.close() }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        workers.shutdownNow()
        acceptThread?.interrupt()
    }
}
