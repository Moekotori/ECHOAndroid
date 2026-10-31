package app.echo.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One bounded immutable catalog, loaded only when the correction screen is used. No network dependency. */
class AutoEqHeadphoneCorrectionRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private val mutex = Mutex()
    private var catalog: AutoEqCatalog? = null

    suspend fun search(query: String, source: String? = null): Result<OpraSearchResult> = withContext(Dispatchers.Default) {
        try {
            val loaded = mutex.withLock {
                catalog ?: withContext(Dispatchers.IO) {
                    // The asset merger expands *.gz and strips that suffix. Keep this GZIP
                    // payload under *.gzip so the packaged path and encoding stay intact.
                    AutoEqCatalog.read(assets.open("autoeq/catalog-v1.tsv.gzip"))
                }.also { catalog = it }
            }
            Result.success(OpraSearchResult(loaded.search(query, source), loaded.status, loaded.brands))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
