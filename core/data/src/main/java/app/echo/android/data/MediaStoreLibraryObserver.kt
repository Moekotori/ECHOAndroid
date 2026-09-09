package app.echo.android.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaStoreLibraryObserver(
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 2_000L,
    private val onChanged: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var debounceJob: Job? = null
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = schedule()
        override fun onChange(selfChange: Boolean, uri: Uri?) = schedule()
    }
    private var registered = false

    fun start() {
        if (registered) return
        resolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        registered = true
    }

    fun stop() {
        if (!registered) return
        resolver.unregisterContentObserver(observer)
        registered = false
        debounceJob?.cancel()
        debounceJob = null
    }

    private fun schedule() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            onChanged()
        }
    }
}
