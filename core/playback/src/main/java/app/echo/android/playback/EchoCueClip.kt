package app.echo.android.playback

import app.echo.android.model.library.CueSheetPolicy

/** Physical source coordinates, carried by each decoded stream rather than the UI's current item. */
internal data class EchoCueClip(val id: String, val uri: String, val startUs: Long, val endUs: Long) {
    fun continuesInto(next: EchoCueClip?): Boolean = next != null &&
        CueSheetPolicy.baseTrackId(id) == CueSheetPolicy.baseTrackId(next.id) && uri == next.uri &&
        endUs > startUs && endUs == next.startUs
}

/** Number of complete PCM frames before a boundary; bounded before multiplying to avoid overflow. */
internal fun cueFramesBefore(deltaUs: Long, rate: Int, frames: Int): Int {
    if (deltaUs <= 0L) return 0
    if (deltaUs >= (frames.toLong() * 1_000_000L + rate - 1) / rate) return frames
    return ((deltaUs * rate + 999_999L) / 1_000_000L).toInt()
}
