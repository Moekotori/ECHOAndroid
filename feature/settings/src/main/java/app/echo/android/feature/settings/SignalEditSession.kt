package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/** Continuous previews are coalesced at 80 ms; gesture end saves the final value immediately. */
internal class SignalEditSession<T : Any>(
    private val onPreview: (T) -> Unit,
    private val onCommit: (T) -> Unit,
) {
    private var pending: T? = null
    private var lastPreview: T? = null
    private var original: T? = null
    private var editing by mutableStateOf(false)
    private var undoEntry by mutableStateOf<Pair<T, T>?>(null)
    val changes = Channel<Unit>(Channel.CONFLATED)

    fun preview(value: T, before: T? = null) {
        if (!editing) original = before
        editing = true
        pending = value
        changes.trySend(Unit)
    }

    fun previewPending() {
        val value = pending ?: return
        if (value != lastPreview) {
            lastPreview = value
            onPreview(value)
        }
    }

    fun commit(value: T, before: T? = null) {
        val previous = original ?: before
        val wasEditing = editing
        pending = null
        lastPreview = null
        original = null
        editing = false
        if (previous == value) {
            // A drag back to its starting point still needs its final audible preview,
            // but should neither write settings nor replace the previous undo entry.
            if (wasEditing) onPreview(value)
            return
        }
        undoEntry = previous?.let { it to value }
        onCommit(value)
    }

    fun finish() {
        pending?.let { commit(it) }
    }

    fun currentOr(value: T): T = pending ?: value

    fun canUndo(current: T): Boolean = !editing && undoEntry?.second == current

    fun undo(current: T) {
        if (!canUndo(current)) return
        val previous = undoEntry?.first ?: return
        undoEntry = null
        onCommit(previous)
    }
}

@Composable
internal fun <T : Any> rememberSignalEditSession(
    onPreview: ((T) -> Unit)?,
    onCommit: (T) -> Unit,
): SignalEditSession<T> {
    val preview = rememberUpdatedState(onPreview)
    val commit = rememberUpdatedState(onCommit)
    val session = remember { SignalEditSession<T>({ preview.value?.invoke(it) }, { commit.value(it) }) }
    LaunchedEffect(session) {
        for (ignored in session.changes) {
            session.previewPending()
            delay(80)
        }
    }
    // Navigation/cancelled gestures must not leave an audible but unsaved edit behind.
    DisposableEffect(session) {
        onDispose {
            session.finish()
            session.changes.close()
        }
    }
    return session
}
