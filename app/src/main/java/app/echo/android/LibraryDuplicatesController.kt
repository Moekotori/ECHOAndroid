package app.echo.android

import android.content.Context
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.LibraryDuplicateRepository
import app.echo.android.data.LibraryDuplicateScanInProgressException
import app.echo.android.feature.library.LibraryDuplicatesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** App wiring owns jobs; the data module owns analysis and the feature owns presentation. */
internal class LibraryDuplicatesController(context: Context, scope: CoroutineScope, private val canAnalyze: () -> Boolean) {
    private val repository = LibraryDuplicateRepository(EchoLibraryDatabase.create(context), context)
    private val owner = scope
    private val progress = MutableStateFlow<Int?>(null)
    private val failed = MutableStateFlow(false)
    private val scanBlocked = MutableStateFlow(false)
    private val cancelling = MutableStateFlow(false)
    private var analysis: Job? = null
    private val notices = combine(failed, scanBlocked, cancelling) { error, scan, cancel -> Triple(error, scan, cancel) }
    val state = combine(repository.observeSummary(), progress, notices) { summary, checked, notice ->
        LibraryDuplicatesState(summary.enabled, summary.groups, summary.hidden, summary.updatedAt, summary.skipped,
            checked != null, checked ?: 0, notice.first, notice.second, notice.third)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), LibraryDuplicatesState())

    fun analyze() {
        if (progress.value != null) return
        if (!canAnalyze()) { scanBlocked.value = true; failed.value = false; return }
        progress.value = 0; failed.value = false; scanBlocked.value = false; cancelling.value = false
        analysis = owner.launch {
            try { repository.analyze(shouldContinue = canAnalyze) { progress.value = it } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: LibraryDuplicateScanInProgressException) { scanBlocked.value = true }
            catch (_: Exception) { failed.value = true }
            finally { progress.value = null; cancelling.value = false }
        }
    }
    fun cancel() { if (progress.value != null) { cancelling.value = true; analysis?.cancel() } }
    fun setEnabled(enabled: Boolean) {
        if (progress.value != null) return
        owner.launch {
            try { repository.setEnabled(enabled); failed.value = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed.value = true }
        }
    }
    fun displayTrackId(id: String?) = repository.displayTrackId(id)
}
