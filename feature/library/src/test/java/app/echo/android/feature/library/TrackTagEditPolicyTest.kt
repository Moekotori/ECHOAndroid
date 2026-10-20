package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack
import org.junit.Assert.*
import org.junit.Test

class TrackTagEditPolicyTest {
    @Test fun fileTagEditingExcludesVirtualCueAndClippedTracks() {
        val track = EchoTrack("mediastore:1", "content://media/1", "Title", "Artist")
        assertTrue(track.canEditFileTags())
        assertFalse(track.copy(id = "mediastore:1#cue:1").canEditFileTags())
        assertFalse(track.copy(clipEndMs = 1000).canEditFileTags())
    }
}
