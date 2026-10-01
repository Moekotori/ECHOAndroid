package app.echo.android.model.library

import app.echo.android.model.connect.EchoRemoteTrack

data class EchoUnifiedSearch(
    val tracks: List<EchoTrack> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<EchoPlaylist> = emptyList(),
    val moments: List<EchoSavedMoment> = emptyList(),
    val pcTracks: List<EchoRemoteTrack> = emptyList(),
    val pcUnavailable: Boolean = false,
    val localUnavailable: Boolean = false,
)
