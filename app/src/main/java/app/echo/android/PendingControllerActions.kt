package app.echo.android

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController

@UnstableApi
internal data class PendingControllerAction(
    val replacesQueue: Boolean,
    val playWhenReady: Boolean?,
    val action: MediaController.() -> Unit,
)

/** Holds only a bounded set of user intents while the session reconnects. */
@UnstableApi
internal class PendingControllerActions : Iterable<PendingControllerAction> {
    private val actions = ArrayDeque<PendingControllerAction>()
    val playWhenReady: Boolean?
        get() = actions.lastOrNull { it.playWhenReady != null }?.playWhenReady

    fun add(action: PendingControllerAction) {
        // A new selection supersedes commands (and captured queues) for the old selection.
        if (action.replacesQueue) actions.clear()
        if (action.playWhenReady != null && !action.replacesQueue &&
            actions.lastOrNull()?.let { !it.replacesQueue && it.playWhenReady != null } == true) {
            actions.removeLast()
        }
        if (actions.size == MaxActions) {
            // Keep the queue anchor and final play/pause intent when dropping old edits.
            val editIndex = actions.indexOfFirst { !it.replacesQueue && it.playWhenReady == null }
            val dropIndex = if (editIndex >= 0) editIndex
                else actions.indexOfFirst { !it.replacesQueue }.coerceAtLeast(0)
            actions.removeAt(dropIndex)
        }
        actions.addLast(action)
    }

    fun clear() = actions.clear()
    fun isNotEmpty(): Boolean = actions.isNotEmpty()
    fun removeFirst(): PendingControllerAction = actions.removeFirst()
    override fun iterator(): Iterator<PendingControllerAction> = actions.iterator()

    companion object { const val MaxActions = 32 }
}
