package app.echo.android.smb

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 进程内共享的 SMB 连接。每个「主机 + 端口 + 共享 + 用户」复用一个会话，
 * 扫描和播放不必各自握手。连接断开后下一次使用时重建；空闲超过 [IdleCloseMs] 的会话会被关掉。
 */
object EchoSmbConnections {
    private const val IdleCloseMs = 5 * 60_000L

    private val client: SMBClient by lazy {
        SMBClient(
            SmbConfig.builder()
                .withTimeout(20, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .build(),
        )
    }

    private class Entry(val session: Session, val share: DiskShare) {
        @Volatile var lastUsedAtMs: Long = System.currentTimeMillis()
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val credentials = ConcurrentHashMap<String, EchoSmbCredential>()

    /** 播放时按共享键找凭据；由应用在读取设置后登记。 */
    fun replaceCredentials(endpoints: List<EchoSmbEndpoint>) {
        val next = endpoints.associate { it.shareKey to it.credential }
        credentials.keys.retainAll(next.keys)
        credentials.putAll(next)
    }

    fun hasCredentialFor(location: EchoSmbLocation): Boolean = credentials.containsKey(location.shareKey)

    /** 用登记过的凭据打开共享；没有登记时按访客尝试。 */
    fun share(location: EchoSmbLocation): DiskShare {
        val credential = credentials[location.shareKey] ?: EchoSmbCredential("", "")
        return share(location.host, location.port, location.share, credential)
    }

    fun share(endpoint: EchoSmbEndpoint): DiskShare =
        share(endpoint.normalizedHost, endpoint.port, endpoint.normalizedShare, endpoint.credential)

    @Synchronized
    private fun share(host: String, port: Int, shareName: String, credential: EchoSmbCredential): DiskShare {
        closeIdle()
        val key = EchoSmbPaths.shareKey(host, port, shareName) + "|" + credential.username.lowercase()
        entries[key]?.let { entry ->
            if (entry.share.isConnected) {
                entry.lastUsedAtMs = System.currentTimeMillis()
                return entry.share
            }
            entries.remove(key)
            runCatching { entry.session.close() }
        }
        val connection = client.connect(host, port)
        val auth = if (credential.username.isBlank()) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(credential.username, credential.password.toCharArray(), credential.domain.ifBlank { null })
        }
        val session = connection.authenticate(auth)
        val share = session.connectShare(shareName) as? DiskShare
            ?: run {
                runCatching { session.close() }
                throw IOException("SMB share \"$shareName\" is not a disk share")
            }
        entries[key] = Entry(session, share)
        return share
    }

    /** 共享失效（例如 NAS 重启）时丢掉缓存的会话，下次重新连接。 */
    fun invalidate(share: DiskShare) {
        val stale = entries.entries.firstOrNull { it.value.share === share } ?: return
        entries.remove(stale.key)
        runCatching { stale.value.session.close() }
    }

    private fun closeIdle() {
        val now = System.currentTimeMillis()
        entries.entries.removeIf { (_, entry) ->
            val idle = now - entry.lastUsedAtMs > IdleCloseMs
            if (idle) runCatching { entry.session.close() }
            idle
        }
    }
}
