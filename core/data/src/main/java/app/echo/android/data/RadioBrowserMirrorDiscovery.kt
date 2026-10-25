package app.echo.android.data

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Official SRV discovery is demand-driven and bounded; older Android uses the HTTPS fallbacks. */
object RadioBrowserMirrorDiscovery {
    suspend fun discover(): List<String> {
        if (Build.VERSION.SDK_INT < 29) return emptyList()
        return withTimeoutOrNull(1_500) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                try {
                    DnsResolver.getInstance().rawQuery(null, "_api._tcp.radio-browser.info", 1, 33, 0,
                        Executor { it.run() }, cancellation, object : DnsResolver.Callback<ByteArray> {
                            override fun onAnswer(answer: ByteArray, rcode: Int) {
                                if (continuation.isActive) continuation.resume(if (rcode == 0) RadioBrowserSrvParser.parse(answer) else emptyList())
                            }
                            override fun onError(error: DnsResolver.DnsException) { if (continuation.isActive) continuation.resume(emptyList()) }
                        })
                } catch (_: Exception) { if (continuation.isActive) continuation.resume(emptyList()) }
            }
        } ?: emptyList()
    }
}

internal object RadioBrowserSrvParser {
    fun parse(bytes: ByteArray): List<String> = try {
        require(bytes.size in 12..65_535)
        fun word(offset: Int): Int { require(offset >= 0 && offset + 1 < bytes.size); return ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255) }
        fun name(start: Int): Pair<String, Int> {
            var offset = start
            var end = -1
            var labels = 0
            val result = ArrayList<String>()
            while (true) {
                require(offset in bytes.indices && labels++ < 64)
                val count = bytes[offset].toInt() and 255
                if (count == 0) { if (end < 0) end = offset + 1; break }
                if (count and 0xC0 == 0xC0) {
                    if (end < 0) end = offset + 2
                    offset = word(offset) and 0x3FFF
                } else {
                    require(count <= 63 && offset + count < bytes.size)
                    result += String(bytes, offset + 1, count, Charsets.US_ASCII)
                    offset += count + 1
                }
            }
            return result.joinToString(".").lowercase() to end
        }
        var offset = 12
        repeat(word(4).coerceAtMost(8)) { offset = name(offset).second + 4 }
        val hosts = ArrayList<Pair<Int, String>>()
        repeat(word(6).coerceAtMost(32)) {
            offset = name(offset).second
            val type = word(offset)
            val length = word(offset + 8)
            offset += 10
            require(offset + length <= bytes.size)
            if (type == 33 && length >= 7) {
                val host = name(offset + 6).first
                if (host.matches(Regex("[a-z0-9-]+\\.api\\.radio-browser\\.info"))) hosts += word(offset) to "https://$host"
            }
            offset += length
        }
        hosts.sortedBy { it.first }.map { it.second }.distinct().take(8)
    } catch (_: Exception) { emptyList() }
}
