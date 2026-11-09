package app.echo.android.data

import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

internal class OneDriveTokens(val access: String, val refresh: String, val expiresAt: Long)

internal class OneDriveAuthorization(private val clientId: String) {
    val available = ClientIdPattern.matches(clientId)

    suspend fun authorize(openBrowser: suspend (String) -> Unit): OneDriveTokens = withContext(Dispatchers.IO) {
        check(available)
        val state = randomToken()
        val verifier = randomToken()
        val challenge = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val (code, redirect) = ServerSocket(0, 4, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))).use { server ->
            server.soTimeout = 350
            val redirect = "http://localhost:${server.localPort}/"
            val address = "$Authority/authorize".toHttpUrl().newBuilder()
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("response_mode", "query")
                .addQueryParameter("redirect_uri", redirect)
                .addQueryParameter("scope", Scope)
                .addQueryParameter("state", state)
                .addQueryParameter("code_challenge", challenge)
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("prompt", "select_account")
                .build()
            withContext(Dispatchers.Main) { openBrowser(address.toString()) }
            awaitCode(server, redirect, state) to redirect
        }
        exchange(mapOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to redirect, "code_verifier" to verifier))
    }

    suspend fun refresh(token: String): OneDriveTokens = exchange(
        mapOf("grant_type" to "refresh_token", "refresh_token" to token),
        previousRefresh = token,
    )

    private suspend fun exchange(parameters: Map<String, String>, previousRefresh: String? = null): OneDriveTokens {
        val form = FormBody.Builder().add("client_id", clientId).add("scope", Scope).apply {
            parameters.forEach { (name, value) -> add(name, value) }
        }.build()
        val result = oneDriveRequest(Request.Builder().url("$Authority/token").post(form).build()) { response ->
            if (!response.isSuccessful) throw IOException("OneDrive authorization failed")
            response.oneDriveJson()
        }
        val access = result.optString("access_token").takeIf { it.isNotBlank() }
        val refresh = result.optString("refresh_token").takeIf { it.isNotBlank() } ?: previousRefresh
        val expires = result.optLong("expires_in").takeIf { it in 1..86_400 }
        if (access == null || refresh == null || expires == null) throw IOException("Incomplete OneDrive authorization")
        return OneDriveTokens(access, refresh, System.currentTimeMillis() + expires * 1_000)
    }

    private suspend fun awaitCode(server: ServerSocket, redirect: String, state: String): String = withContext(Dispatchers.IO) {
        val deadline = System.nanoTime() + 180_000_000_000L
        while (System.nanoTime() < deadline) {
            ensureActive()
            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
            socket.use {
                socket.soTimeout = 750
                val target = try {
                    readLoopbackTarget(BufferedInputStream(socket.getInputStream())) {
                        ensureActive()
                        if (System.nanoTime() >= deadline) throw SocketTimeoutException()
                    }
                } catch (_: IOException) { null }
                val callback = target?.let { parseOneDriveCallback(redirect, it, state) }
                val message = when (callback) {
                    is OneDriveCallback.Code -> "Return to ECHO to finish connecting OneDrive."
                    OneDriveCallback.Denied -> "OneDrive authorization was declined. Return to ECHO."
                    null -> "Invalid ECHO authorization callback."
                }
                runCatching {
                    val body = message.toByteArray(Charsets.UTF_8)
                    val status = if (callback == null) "400 Bad Request" else "200 OK"
                    val header = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(body); flush() }
                }
                when (callback) {
                    is OneDriveCallback.Code -> return@withContext callback.value
                    OneDriveCallback.Denied -> throw IOException("OneDrive authorization was declined")
                    null -> Unit
                }
            }
        }
        throw IOException("OneDrive authorization timed out")
    }

    private companion object {
        const val Authority = "https://login.microsoftonline.com/common/oauth2/v2.0"
        const val Scope = "https://graph.microsoft.com/Files.Read offline_access"
        val ClientIdPattern = Regex("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")
        val random = SecureRandom()
        fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        fun randomToken(): String = encode(ByteArray(32).also(random::nextBytes))
    }
}

private fun readLoopbackTarget(input: BufferedInputStream, checkActive: () -> Unit): String? {
    val header = StringBuilder()
    while (header.length < 16_384) {
        checkActive()
        val next = input.read()
        if (next == -1) return null
        header.append(next.toChar())
        if (header.endsWith("\r\n\r\n")) {
            val parts = header.toString().substringBefore("\r\n").split(' ', limit = 3)
            return parts.getOrNull(1)?.takeIf { parts.size == 3 && parts[0] == "GET" && parts[2].startsWith("HTTP/1.") }
        }
    }
    return null
}

internal sealed interface OneDriveCallback {
    class Code(val value: String) : OneDriveCallback
    data object Denied : OneDriveCallback
}

internal fun parseOneDriveCallback(redirect: String, target: String, state: String): OneDriveCallback? {
    if (!target.startsWith("/?") || '#' in target) return null
    val url = (redirect.removeSuffix("/") + target).toHttpUrl()
    if (url.queryParameterValues("state").singleOrNull() != state) return null
    if (url.queryParameter("error") != null) return OneDriveCallback.Denied
    return url.queryParameterValues("code").singleOrNull()?.takeIf { it.isNotBlank() }?.let(OneDriveCallback::Code)
}
