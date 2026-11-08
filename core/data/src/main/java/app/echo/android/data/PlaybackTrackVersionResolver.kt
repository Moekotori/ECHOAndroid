package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackAudioQuality
import app.echo.android.model.library.EchoTrackVersionPolicy

/** Avoid opening queue files when availability cannot change the selected version. */
internal fun resolvePlaybackTrackVersion(
    anchor: EchoTrack,
    candidates: List<EchoTrack>,
    preferredId: String?,
    quality: Map<String, EchoTrackAudioQuality>,
    preferQuality: Boolean,
    hasIndexedGroup: Boolean,
    isPlayable: (EchoTrack) -> Boolean,
): EchoTrack {
    // With no alternative the model returns the anchor even when it is unavailable.
    // Indexed groups still need validation so unavailable members can be invalidated.
    if (!hasIndexedGroup && candidates.none { it.id != anchor.id }) return anchor
    val playableIds = candidates.filter(isPlayable).mapTo(HashSet()) { it.id }
    return EchoTrackVersionPolicy.choose(anchor, candidates, preferredId, playableIds, quality, preferQuality)
}
