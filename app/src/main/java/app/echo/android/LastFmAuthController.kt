package app.echo.android

import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.LastFmApiException
import app.echo.android.data.LastFmClient
import app.echo.android.data.LastFmPendingAuth
import app.echo.android.data.LastFmPendingAuthStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The pending token and its matching app credentials survive process recreation. */
internal class LastFmAuthController(
    private val scope: CoroutineScope,
    private val settings: EchoSettingsStore,
    private val pendingStore: LastFmPendingAuthStore,
    private val ui: LastFmScrobbleController,
    private val client: LastFmClient = LastFmClient(),
) {
    private var pending: LastFmPendingAuth? = null
    private var command: Job? = null
    private val restore = scope.launch {
        try {
            pending = withContext(Dispatchers.IO) { pendingStore.read() }
            ui.clearAuthorizationState()
            if (pending != null) ui.setWebAuthPending()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { ui.setError("Unable to restore Last.fm authorization") }
    }

    fun start(apiKey: String, secret: String, open: (String) -> Unit) {
        if (command?.isActive == true) return
        command = scope.launch {
            restore.join()
            val key = apiKey.trim().ifBlank { LastFmApiConfig.API_KEY }
            val sharedSecret = secret.trim().ifBlank { LastFmApiConfig.SHARED_SECRET }
            if (key.isBlank() || sharedSecret.isBlank()) { ui.setError("Missing Last.fm app credentials"); return@launch }
            ui.setConnecting()
            try {
                val result = client.createWebAuthToken(key, sharedSecret).getOrThrow()
                val auth = LastFmPendingAuth(key, sharedSecret, result.token, System.currentTimeMillis())
                withContext(Dispatchers.IO) { pendingStore.write(auth) }
                currentCoroutineContext().ensureActive()
                pending = auth
                ui.setWebAuthPending()
                open(result.url)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { ui.setError(error.message ?: "Unable to open Last.fm authorization") }
        }
    }

    fun complete(automatic: Boolean = false) {
        if (command?.isActive == true) return
        command = scope.launch {
            restore.join()
            val auth = pending ?: return@launch
            ui.setConnecting()
            try {
                val session = client.completeWebAuth(auth.apiKey, auth.sharedSecret, auth.token).getOrThrow()
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.IO) {
                    settings.setLastFmCredentials(auth.apiKey, auth.sharedSecret, session.username, session.sessionKey)
                    pendingStore.clear()
                }
                pending = null
                ui.setConnected(session.username)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (error is LastFmApiException && error.code in setOf(4, 15)) {
                    pending = null
                    withContext(Dispatchers.IO) { pendingStore.clear() }
                    ui.setError(error.message ?: "Last.fm authorization expired")
                } else if (automatic && error is LastFmApiException && error.code == 14) {
                    ui.setWebAuthPending()
                } else ui.setWebAuthError(error.message ?: "Last.fm authorization has not been approved")
            }
        }
    }

    suspend fun cancelPending() {
        command?.cancelAndJoin()
        restore.join()
        pending = null
        withContext(Dispatchers.IO) { pendingStore.clear() }
        ui.clearAuthorizationState()
    }
}
