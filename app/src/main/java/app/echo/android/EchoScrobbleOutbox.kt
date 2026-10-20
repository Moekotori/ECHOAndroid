package app.echo.android

import app.echo.android.data.PendingScrobble
import app.echo.android.data.ScrobbleOutboxStore
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class ScrobbleAccount<C>(val fingerprint: String, val credentials: C, val enabled: Boolean)
private data class ScrobbleConfiguration<C>(val account: ScrobbleAccount<C>?)

/** A single serial sender per service. Persist before send, retry only while enabled. */
internal class EchoScrobbleOutbox<C>(
    scope: CoroutineScope,
    private val store: ScrobbleOutboxStore,
    private val submit: suspend (C, PendingScrobble) -> Result<Unit>,
    private val status: (PendingScrobble?, Int, Throwable?) -> Unit,
) {
    // Null means settings have not loaded yet; a configured null account means signed out.
    private val configuration = MutableStateFlow<ScrobbleConfiguration<C>?>(null)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            configuration.collectLatest { configured ->
                if (configured == null) return@collectLatest
                val owner = configured.account
                var lastFailure: Throwable? = null
                while (true) {
                    try {
                        store.retainAccount(owner?.fingerprint)
                        if (owner == null || !owner.enabled) return@collectLatest
                        val pending = store.read().filter { it.account == owner.fingerprint }
                        status(null, pending.size, lastFailure)
                        val next = pending.firstOrNull()
                        if (next == null) { wake.receive(); continue }
                        val waitMs = next.retryAtEpochMs - System.currentTimeMillis()
                        if (waitMs > 0) {
                            withTimeoutOrNull(waitMs) { wake.receive() }
                            continue
                        }
                        val result = submit(owner.credentials, next)
                        currentCoroutineContext().ensureActive()
                        if (result.isSuccess) {
                            store.acknowledge(next.key)
                            lastFailure = null
                            status(next, pending.size - 1, null)
                        } else {
                            val error = result.exceptionOrNull()!!
                            if (error is CancellationException) throw error
                            store.fail(next, System.currentTimeMillis())
                            lastFailure = error
                            status(next, pending.size, error)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // Preserve the file and retry disk errors with a bounded wake frequency.
                        lastFailure = error
                        status(null, -1, error)
                        withTimeoutOrNull(30_000L) { wake.receive() }
                    }
                }
            }
        }
    }

    fun configure(next: ScrobbleAccount<C>?) {
        configuration.value = ScrobbleConfiguration(next)
    }

    suspend fun enqueue(listen: PendingScrobble): Boolean {
        if (configuration.value?.account?.let { it.enabled && it.fingerprint == listen.account } != true) return false
        store.enqueue(listen)
        val current = configuration.value?.account
        if (current?.fingerprint != listen.account) {
            store.retainAccount(current?.fingerprint)
            return false
        }
        wake.trySend(Unit)
        return true
    }

    fun retryWhenConnected() { wake.trySend(Unit) }
}

internal fun scrobbleFingerprint(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("\u0000").toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
