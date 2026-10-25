package app.echo.android.model.radio

import org.junit.Assert.*
import org.junit.Test

class RadioArtworkPolicyTest {
    @Test fun correctsOnlyKnownBrokenDirectoryArtwork() {
        val broken = "https://www.europe1.fr/build/e1/images/favicon-120x120.png"
        assertEquals("https://db.radioline.fr/pictures/radio_fa64fea9b6ccd92e058ced3fa41f58f5/logo200.jpg", RadioArtworkPolicy.corrected(broken))
        val custom = "https://images.example/my-cover.png"
        assertEquals(custom, RadioArtworkPolicy.corrected(custom))
        assertNull(RadioArtworkPolicy.corrected(null))
    }
}
