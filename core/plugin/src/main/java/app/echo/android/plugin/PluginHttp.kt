package app.echo.android.plugin

import java.net.URI

data class PluginHttpExchange(
    val status: Int,
    val location: String?,
    val body: ByteArray,
)

object PluginHttp {
    const val MaxBodyBytes: Int = 256 * 1024
    private const val MaxRedirects = 3
    private const val MaxUrlLength = 2_000

    fun fetch(url: String, open: (URI) -> PluginHttpExchange): PluginHttpResponse {
        val initial = parseAllowed(url) ?: return failed("invalid")
        var current = initial
        repeat(MaxRedirects + 1) { attempt ->
            val exchange = runCatching { open(current) }.getOrNull() ?: return failed("failed")
            val redirect = exchange.status in RedirectStatuses
            if (!redirect) {
                val body = exchange.body.copyOf(exchange.body.size.coerceAtMost(MaxBodyBytes))
                return PluginHttpResponse(
                    ok = true,
                    status = exchange.status,
                    body = body.toString(Charsets.UTF_8),
                    error = null,
                )
            }
            if (attempt == MaxRedirects) return failed("failed")
            val location = exchange.location?.takeIf { it.isNotBlank() } ?: return failed("failed")
            current = runCatching { current.resolve(location) }.getOrNull() ?: return failed("failed")
            if (!isAllowed(current)) return failed("invalid")
        }
        return failed("failed")
    }

    internal fun parseAllowed(url: String): URI? {
        if (url.length !in 1..MaxUrlLength) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return uri.takeIf(::isAllowed)
    }

    private fun isAllowed(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return false
        return true
    }

    private fun failed(error: String) = PluginHttpResponse(ok = false, status = 0, body = "", error = error)

    private val RedirectStatuses = setOf(301, 302, 303, 307, 308)
}
