package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local edits always win over rescanned file tags; a cleared override remains cleared. */
internal fun LibraryTrackEntity.withPreservedRating(existing: LibraryTrackEntity?): LibraryTrackEntity {
    val local = if (existing != null) existing.localRating else localRating
    val embedded = embeddedRating ?: existing?.embeddedRating
    return copy(localRating = local, embeddedRating = embedded, rating = (local ?: embedded ?: rating).coerceIn(0, 5))
}

suspend fun EchoLibraryRepository.setLocalTrackRating(id: String, value: Int?): Int = withContext(Dispatchers.IO) {
    require(value == null || value in 0..5)
    val dao = database.trackDao()
    val changed = if (value != null) dao.setLocalRating(id, value) else {
        val track = dao.getTrackById(id) ?: error("Track no longer exists")
        require(track.source == "mediastore" || track.source == "saf")
        val uri = android.net.Uri.parse(CueSheetPolicy.playbackUri(track.contentUri))
        val input = appContext.contentResolver.openInputStream(uri) ?: error("Unable to read file rating")
        val tags = input.use(::readLocalAudioTags)
        dao.useEmbeddedRating(id, (tags?.rating ?: 0).coerceIn(0, 5))
    }
    check(changed > 0) { "Track is not in the local music library" }
    database.trackDao().getTrackById(id)?.rating ?: 0
}
