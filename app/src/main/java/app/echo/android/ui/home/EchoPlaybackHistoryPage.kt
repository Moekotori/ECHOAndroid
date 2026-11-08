package app.echo.android.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.PlaybackHistoryController
import app.echo.android.feature.home.PlaybackHistoryActions
import app.echo.android.feature.home.PlaybackHistoryMarkdown
import app.echo.android.feature.home.PlaybackHistoryMarkdownLabels
import app.echo.android.feature.home.PlaybackHistoryScreen
import app.echo.android.feature.home.R
import app.echo.android.model.playback.PlaybackHistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZonedDateTime

@Composable
internal fun EchoPlaybackHistoryPage(controller: PlaybackHistoryController, onStats: () -> Unit, onBack: () -> Unit) {
    val filter by controller.filter.collectAsStateWithLifecycle()
    val summary by controller.summary.collectAsStateWithLifecycle()
    val trend by controller.trend.collectAsStateWithLifecycle()
    val entries = controller.entries.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }

    val failed = stringResource(R.string.playback_history_action_error)
    val deleted = stringResource(R.string.playback_history_deleted)
    val cleared = stringResource(R.string.playback_history_cleared)
    val unavailable = stringResource(R.string.playback_history_unavailable)
    val copied = stringResource(R.string.playback_history_copied)
    val exported = stringResource(R.string.playback_history_exported)
    val pruneNone = stringResource(R.string.playback_history_prune_none)
    val copyLabel = stringResource(R.string.playback_history_title)
    val labels = PlaybackHistoryMarkdownLabels(
        heading = stringResource(R.string.playback_history_title),
        exported = "",
        time = stringResource(R.string.playback_history_col_time),
        title = stringResource(R.string.playback_history_sort_title),
        artist = stringResource(R.string.playback_history_sort_artist),
        album = stringResource(R.string.playback_history_col_album),
        plays = stringResource(R.string.listening_stats_total_plays),
        listened = stringResource(R.string.listening_stats_total_time),
        lastPlayed = stringResource(R.string.playback_history_sort_recent),
    )
    val exportedAt = stringResource(
        R.string.playback_history_exported_at,
        remember(locale) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
                .format(ZonedDateTime.now(ZoneId.systemDefault()))
        },
    )

    /** Run a controller action with one busy flag, report a result message, and never swallow cancellation. */
    fun act(action: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            val message = try {
                action()
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

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) act {
            val grouping = controller.filter.value.grouping
            val rows = controller.exportRows()
            val text = PlaybackHistoryMarkdown.render(rows, grouping, labels.copy(exported = exportedAt), ZoneId.systemDefault(), locale)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            exported
        }
    }

    val actions = PlaybackHistoryActions(
        onFilter = controller::update,
        onReset = controller::reset,
        onPlay = { entry -> act { if (controller.replay(entry.trackId)) null else unavailable } },
        onPlayNext = { entry -> controller.playNext(entry.trackId) },
        onEnqueue = { entry -> controller.enqueue(entry.trackId) },
        onCopy = { entry ->
            copyToClipboard(context, copyLabel, entry)
            scope.launch { snackbar.showSnackbar(copied) }
        },
        onDelete = { id -> act { controller.delete(id); deleted } },
        onClear = { act { controller.clear(); cleared } },
        onPrune = {
            act {
                val removed = controller.removeUnavailable()
                if (removed == 0) pruneNone
                else context.resources.getQuantityString(R.plurals.playback_history_pruned, removed, removed)
            }
        },
        onExport = { exportFile.launch("echo-playback-history.md") },
        onStats = onStats,
        onBack = onBack,
    )

    Box(Modifier.fillMaxSize()) {
        PlaybackHistoryScreen(entries, filter, summary, trend, busy, actions)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

private fun copyToClipboard(context: Context, label: String, entry: PlaybackHistoryEntry) {
    val text = listOf(entry.title, entry.artist).filter { it.isNotBlank() }.joinToString(" - ")
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}
