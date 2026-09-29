package app.echo.android.smb

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * 一个 SMB 共享目录。[basePath] 是共享内的起始目录（可为空，表示共享根目录），
 * 用 `/` 分隔，不带首尾斜杠。用户名为空时以访客身份连接。
 */
data class EchoSmbEndpoint(
    val host: String,
    val share: String,
    val basePath: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val port: Int = DefaultPort,
) {
    val normalizedHost: String = host.trim().lowercase(Locale.ROOT)
    val normalizedShare: String = share.trim().trim('/', '\\')
    val normalizedBasePath: String = EchoSmbPaths.normalize(basePath)
    val isGuest: Boolean get() = username.isBlank()

    /** 库里的 source 字段，同一台主机 + 共享 + 用户是同一个来源。 */
    val sourceId: String =
        "smb:" + stableHash("$normalizedHost:$port/${normalizedShare.lowercase(Locale.ROOT)}|${username.trim()}")

    val credential: EchoSmbCredential
        get() = EchoSmbCredential(username.trim(), password, domain.trim())

    /** 凭据登记和连接池用的键：主机 + 端口 + 共享。 */
    val shareKey: String get() = EchoSmbPaths.shareKey(normalizedHost, port, normalizedShare)

    companion object {
        const val DefaultPort = 445

        /**
         * 解析用户输入的地址。接受 `smb://host/share/dir`、`\\host\share\dir`、`host/share`，
         * 以及带端口的 `host:1445/share`。无法识别时返回 null。
         */
        fun parseAddress(input: String): EchoSmbAddress? {
            var text = input.trim().replace('\\', '/')
            if (text.startsWith("smb://", ignoreCase = true)) text = text.substring(6)
            text = text.trimStart('/')
            val parts = text.split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val hostPart = parts[0]
            val host: String
            val port: Int
            if (hostPart.startsWith("[")) {
                val end = hostPart.indexOf(']')
                if (end < 0) return null
                host = hostPart.substring(1, end)
                port = hostPart.substring(end + 1).removePrefix(":").toIntOrNull() ?: DefaultPort
            } else if (hostPart.count { it == ':' } == 1) {
                host = hostPart.substringBefore(':')
                port = hostPart.substringAfter(':').toIntOrNull() ?: return null
            } else {
                host = hostPart
                port = DefaultPort
            }
            if (host.isBlank() || port !in 1..65535) return null
            return EchoSmbAddress(
                host = host,
                port = port,
                share = parts[1],
                basePath = parts.drop(2).joinToString("/"),
            )
        }

        private fun stableHash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}

data class EchoSmbAddress(val host: String, val port: Int, val share: String, val basePath: String)

data class EchoSmbCredential(
    val username: String,
    val password: String,
    val domain: String = "",
)

/** smb:// 播放地址与共享内路径之间的换算。 */
object EchoSmbPaths {
    const val Scheme = "smb"

    fun normalize(path: String): String =
        path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }.joinToString("/")

    fun shareKey(host: String, port: Int, share: String): String =
        "${host.trim().lowercase(Locale.ROOT)}:$port/${share.trim().trim('/', '\\').lowercase(Locale.ROOT)}"

    /** 共享内路径 → 可放进曲库、交给播放器的 URI。路径段会做百分号编码。 */
    fun uri(host: String, port: Int, share: String, path: String): String {
        val normalized = normalize(path)
        val fullPath = "/" + listOf(share.trim('/', '\\'), normalized).filter { it.isNotEmpty() }.joinToString("/")
        return URI(Scheme, null, host, if (port == EchoSmbEndpoint.DefaultPort) -1 else port, fullPath, null, null)
            .toASCIIString()
    }

    fun isSmbUri(uri: String): Boolean = uri.startsWith("$Scheme://", ignoreCase = true)

    /** 播放地址 → (共享键, 共享名, 共享内路径)。不是 smb 地址或缺少共享名时返回 null。 */
    fun parse(uri: String): EchoSmbLocation? {
        if (!isSmbUri(uri)) return null
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        val host = parsed.host?.takeIf { it.isNotBlank() } ?: return null
        val port = parsed.port.takeIf { it > 0 } ?: EchoSmbEndpoint.DefaultPort
        val segments = (parsed.path ?: return null).split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return null
        return EchoSmbLocation(
            host = host,
            port = port,
            share = segments[0],
            path = segments.drop(1).joinToString("/"),
        )
    }
}

data class EchoSmbLocation(val host: String, val port: Int, val share: String, val path: String) {
    val shareKey: String get() = EchoSmbPaths.shareKey(host, port, share)
}
