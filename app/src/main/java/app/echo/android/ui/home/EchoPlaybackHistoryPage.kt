package app.echo.android.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.PlaybackHistoryController
import app.echo.android.feature.home.PlaybackHistoryScreen
import app.echo.android.feature.home.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun EchoPlaybackHistoryPage(controller: PlaybackHistoryController, onStats: () -> Unit, onBack: () -> Unit) {
    val query by controller.query.collectAsStateWithLifecycle()
    val range by controller.range.collectAsStateWithLifecycle()
    val entries = controller.entries.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.playback_history_action_error)
    val deleted = stringResource(R.string.playback_history_deleted)
    val cleared = stringResource(R.string.playback_history_cleared)
    val unavailable = stringResource(R.string.playback_history_unavailable)
    fun act(success: String?, action: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val message = try {
                if (action()) success else unavailable
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed
            } finally {
                busy = false
            }
            if (message != null) snackbar.showSnackbar(message)
        }
    }
    Box(Modifier.fillMaxSize()) {
        PlaybackHistoryScreen(entries, query, range, busy,
            onQuery = controller::setQuery, onRange = controller::setRange,
            onPlay = { entry -> act(null) { controller.replay(entry.trackId) } },
            onDelete = { id -> act(deleted) { controller.delete(id); true } },
            onClear = { act(cleared) { controller.clear(); true } }, onStats = onStats, onBack = onBack)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
