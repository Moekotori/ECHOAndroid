package app.echo.android.data

import app.echo.android.model.settings.LastFmLoveSyncStatus
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private data class LoveSyncProfile(val username: String, val credentials: LastFmCredentials)

class LastFmLoveSync(
    scope: CoroutineScope,
    settings: Flow<EchoAppSettings>,
    private val database: EchoLibraryDatabase,
    private val file: File,
    private val client: LastFmClient = LastFmClient(),
    private val invalidSession: suspend (LastFmCredentials) -> Unit,
) {
    private val state = MutableStateFlow(LastFmLoveSyncStatus())
    val status = state.asStateFlow()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val retryRequests = Channel<Unit>(Channel.CONFLATED)
    fun retry() { retryRequests.trySend(Unit); wake.trySend(Unit) }

    init {
        scope.launch(Dispatchers.IO) {
            settings.map { value ->
                if (!value.lastFmEnabled || !value.lastFmOptions.loveSyncEnabled) null
                else value.lastFmCredentialsOrNull()?.let { LoveSyncProfile(value.lastFmUsername.orEmpty().lowercase(Locale.ROOT), it) }
            }.distinctUntilChanged().collectLatest { profile ->
                state.value = LastFmLoveSyncStatus()
                if (profile == null || profile.username.isBlank()) return@collectLatest
                coroutineScope {
                    val store = LastFmLoveStore(file)
                    val scan = AtomicBoolean(true)
                    val watcher = launch { database.lastFmDao().favoriteChanges().collect { scan.set(true); wake.trySend(Unit) } }
                    var loaded = false
                    var failures = 0
                    while (true) {
                        try {
                            if (!loaded) { store.load(profile.username); loaded = true }
                            if (scan.getAndSet(false)) {
                                state.value = state.value.copy(scanning = true)
                                var after = ""
                                do {
                                    val rows = database.lastFmDao().favoritePage(after)
                                    currentCoroutineContext().ensureActive()
                                    store.enqueue(rows)
                                    after = rows.lastOrNull()?.id ?: after
                                } while (rows.size == 200)
                            }
                            state.value = LastFmLoveSyncStatus(synced = store.synced, pending = store.pendingCount,
                                capacityReached = store.capacityReached, failed = store.rejectedCount > 0)
                            val next = store.nextPending
                            if (next == null) { wake.receive(); continue }
                            val result = client.love(profile.credentials, next.title, next.artist)
                            currentCoroutineContext().ensureActive()
                            val error = result.exceptionOrNull()
                            if (error is LastFmApiException && !error.retryable && !error.requiresAuthorization) store.reject(next)
                            else { result.getOrThrow(); store.sent(next) }
                            failures = 0
                            state.value = state.value.copy(synced = store.synced, pending = store.pendingCount, failed = store.rejectedCount > 0)
                            delay(350)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            state.value = state.value.copy(scanning = false, failed = true)
                            if (error is LastFmApiException && error.requiresAuthorization) {
                                invalidSession(profile.credentials)
                                watcher.cancel()
                                return@coroutineScope
                            }
                            loaded = false
                            scan.set(true)
                            val backoff = minOf(300_000L, 1000L shl failures.coerceAtMost(8))
                            failures++
                            // New local likes must not defeat backoff during a failing import/sync.
                            withTimeoutOrNull(backoff) { retryRequests.receive() }
                        }
                    }
                }
            }
        }
    }
}

fun EchoAppSettings.lastFmCredentialsOrNull(): LastFmCredentials? {
    val apiKey = lastFmApiKey?.trim().orEmpty()
    val secret = lastFmSharedSecret?.trim().orEmpty()
    val session = lastFmSessionKey?.trim().orEmpty()
    return if (apiKey.isBlank() || secret.isBlank() || session.isBlank()) null else LastFmCredentials(apiKey, secret, session)
}
