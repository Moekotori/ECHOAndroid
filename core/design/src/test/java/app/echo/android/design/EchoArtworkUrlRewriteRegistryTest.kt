package app.echo.android.design

import org.junit.Assert.*
import org.junit.Test

class EchoArtworkUrlRewriteRegistryTest {
    @Test fun reusesSignatureUntilCredentialsChangeAndEvictsOldResources() {
        var signatures = 0
        try {
            EchoArtworkUrlRewriteRegistry.replace { "$it&signature=${++signatures}" }
            val first = EchoArtworkUrlRewriteRegistry.rewrite("https://server/rest/getCoverArt?id=a")
            repeat(20) { assertEquals(first, EchoArtworkUrlRewriteRegistry.rewrite("https://server/rest/getCoverArt?id=a")) }
            assertEquals(1, signatures)
            EchoArtworkUrlRewriteRegistry.notifyChanged()
            assertNotEquals(first, EchoArtworkUrlRewriteRegistry.rewrite("https://server/rest/getCoverArt?id=a"))
            repeat(513) { EchoArtworkUrlRewriteRegistry.rewrite("resource-$it") }
            val before = signatures
            EchoArtworkUrlRewriteRegistry.rewrite("https://server/rest/getCoverArt?id=a")
            assertEquals(before + 1, signatures)
        } finally { EchoArtworkUrlRewriteRegistry.replace { it } }
    }
}
