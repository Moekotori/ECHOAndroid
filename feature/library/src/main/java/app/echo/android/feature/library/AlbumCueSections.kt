package app.echo.android.feature.library

import app.echo.android.model.library.ArtistSetlistSong
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack

internal fun albumHasCueMovements(tracks: Iterable<EchoTrack>): Boolean {
    val grouped = LinkedHashMap<String, Int>()
    for (track in tracks) {
        if (!CueSheetPolicy.isCueTrackId(track.id)) continue
        val base = CueSheetPolicy.baseTrackId(track.id)
        grouped[base] = (grouped[base] ?: 0) + 1
        if ((grouped[base] ?: 0) >= 2) return true
    }
    return false
}

internal fun cueClipRangeLabel(track: EchoTrack): String? {
    if (!CueSheetPolicy.isCueTrackId(track.id)) return null
    return formatCueClipRange(track.clipStartMs, track.clipEndMs)
}

internal fun formatCueClipRange(startMs: Long, endMs: Long): String? {
    if (endMs <= startMs) return null
    return "${formatCueClock(startMs)}–${formatCueClock(endMs)}"
}

internal fun playableSetlistTrackIds(songs: List<ArtistSetlistSong>): List<String> =
    songs.mapNotNull { song -> song.trackId?.takeIf { it.isNotBlank() } }.distinct()

private fun formatCueClock(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
