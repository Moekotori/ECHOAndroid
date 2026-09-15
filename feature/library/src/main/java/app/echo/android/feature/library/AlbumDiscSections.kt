package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack

internal fun EchoTrack.albumDiscNumber(): Int = discNumber?.takeIf { it > 0 } ?: 1

internal fun albumHasMultipleDiscs(tracks: Iterable<EchoTrack>): Boolean {
    var seen: Int? = null
    for (track in tracks) {
        val disc = track.albumDiscNumber()
        if (seen == null) {
            seen = disc
        } else if (disc != seen) {
            return true
        }
    }
    return false
}

internal fun shouldShowAlbumDiscHeader(
    track: EchoTrack,
    previous: EchoTrack?,
    isFirst: Boolean,
    multiDisc: Boolean,
): Boolean {
    if (!multiDisc) return false
    if (previous == null) return isFirst
    return previous.albumDiscNumber() != track.albumDiscNumber()
}
