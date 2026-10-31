package app.echo.android.feature.library

import androidx.compose.runtime.*
import app.echo.android.model.library.ArtistOnlineQuery
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberServerArtistArtwork(query: ArtistOnlineQuery): String? {
    val loader = LocalArtistOnlineInfoLoader.current
    val artwork by produceState<String?>(null, query, loader) {
        value = null
        try { value = loader?.loadServerProfile(query)?.artworkUri }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
    return artwork
}
