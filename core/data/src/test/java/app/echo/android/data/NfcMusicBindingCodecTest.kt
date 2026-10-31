package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test

class NfcMusicBindingCodecTest {
    private val token = "a45ef725-2ec6-4b65-b178-e05ac3644338"

    @Test fun rebindReplacesOneCardAndKeepsOtherBindings() {
        val other = "dffef725-2ec6-4b65-b178-e05ac3644338"
        val original = mapOf(token to NfcMusicBinding("echo.album.one", "专辑"),
            other to NfcMusicBinding("echo.playlist.two", "歌单"))
        val bindings = NfcMusicBindingCodec.decode(NfcMusicBindingCodec.encode(original)).toMutableMap()
        bindings[token] = NfcMusicBinding("echo.playlist.three", "新歌单")
        val restored = NfcMusicBindingCodec.decode(NfcMusicBindingCodec.encode(bindings))
        assertEquals(original[other], restored[other])
        assertEquals("echo.playlist.three", restored[token]?.mediaId)
    }

    @Test fun invalidAndOversizedPersistedDataIsBounded() {
        assertTrue(NfcMusicBindingCodec.decode("not json").isEmpty())
        assertTrue(NfcMusicBindingCodec.decode(" ".repeat(800_000)).isEmpty())
        assertFalse(NfcMusicBindingCodec.validToken("../../music"))
        assertThrows(IllegalArgumentException::class.java) {
            NfcMusicBindingCodec.encode(mapOf(token to NfcMusicBinding("x".repeat(2049), "album")))
        }
    }
}
