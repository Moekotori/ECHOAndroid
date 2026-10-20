package app.echo.android.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Retained with the editor host so an old task can complete into the recreated UI. */
internal class TrackMetadataSaveState {
    var saving by mutableStateOf(false)
        private set
    var completed by mutableStateOf(false)
        private set

    suspend fun save(operation: suspend () -> Boolean): Boolean {
        if (saving || completed) return false
        saving = true
        return try {
            operation().also { completed = it }
        } finally {
            saving = false
        }
    }

    fun consumeCompletion() {
        completed = false
    }
}
