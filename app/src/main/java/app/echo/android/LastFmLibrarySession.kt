package app.echo.android

import app.echo.android.data.EchoAppSettings
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.LastFmLibraryImporter
import app.echo.android.data.LastFmLoveSync
import app.echo.android.data.lastFmCredentialsOrNull
import app.echo.android.model.settings.LastFmImportStatus
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class LastFmLibrarySession(
    private val scope: CoroutineScope,
    private val settings: Flow<EchoAppSettings>,
    private val store: EchoSettingsStore,
    private val database: EchoLibraryDatabase,
    file: File,
) {
    private val state = MutableStateFlow(LastFmImportStatus())
    val importStatus = state.asStateFlow()
    private var importJob: Job? = null
    private val importRevision = AtomicLong()
    private var importOwner: Triple<Boolean, String?, String?>? = null
    private val loveSync = LastFmLoveSync(scope, settings, database, file) { credentials ->
        if (settings.first().lastFmCredentialsOrNull() == credentials) store.clearLastFmSession(credentials.sessionKey)
    }
    val loveStatus = loveSync.status
    fun retry() = loveSync.retry()

    init {
        scope.launch {
            settings.map { Triple(it.lastFmEnabled, it.lastFmUsername, it.lastFmSessionKey) }
                .distinctUntilChanged().collectLatest { owner ->
                    if (importOwner != null && importOwner != owner) importJob?.cancel()
                }
        }
    }

    fun startImport() {
        if (importJob?.isActive == true) return
        val revision = importRevision.incrementAndGet()
        importJob = scope.launch {
            val current = settings.first()
            val credentials = current.lastFmCredentialsOrNull() ?: return@launch
            val username = current.lastFmUsername?.takeIf(String::isNotBlank) ?: return@launch
            if (!current.lastFmEnabled) return@launch
            importOwner = Triple(current.lastFmEnabled, current.lastFmUsername, current.lastFmSessionKey)
            try {
                LastFmLibraryImporter(database).run(credentials, username) {
                    if (importRevision.get() == revision) state.value = it
                }
            } catch (error: CancellationException) { throw error }
            // Public library-read errors cannot establish that the user's session is invalid.
            catch (_: Exception) { /* The importer already published its failed status. */ }
        }
    }

    fun cancelImport() { importJob?.cancel() }
}
