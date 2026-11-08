package app.echo.android.data

import app.echo.android.model.library.EchoArtistArtwork
import org.junit.Assert.*
import org.junit.Test

class ArtistArtworkPolicyTest {
    @Test fun userPictureWinsEvenAfterLocalDiscoveryRefreshes() {
        val chosen = EchoArtistArtwork("manual", "old-local")
        assertEquals("manual", chosen.copy(localUri = "new-local").preferredUri)
        assertEquals("old-local", chosen.copy(manualUri = null).preferredUri)
    }

    @Test fun localMatchingUsesExactNormalizedArtistNameAndImageExtension() {
        assertTrue(ArtistArtworkStore.matches("artist_ＡＢＢＡ.JPG", "abba"))
        assertFalse(ArtistArtworkStore.matches("artist_ABBA tribute.jpg", "ABBA"))
        assertFalse(ArtistArtworkStore.matches("ABBA.jpg", "ABBA"))
        assertFalse(ArtistArtworkStore.matches("artist_ABBA.jpg.exe", "ABBA"))
    }
}
