package app.echo.android.nfc

import org.junit.Assert.*
import org.junit.Test

class NfcMusicTagTest {
    @Test fun payloadRoundTripsOnlyVersionedRandomIdentifiers() {
        val token = NfcMusicTag.newToken()
        assertEquals(token, NfcMusicTag.parsePayload(NfcMusicTag.payload(token)))
        assertNull(NfcMusicTag.parsePayload("echo-music:2:$token".toByteArray()))
        assertNull(NfcMusicTag.parsePayload("echo-music:1:https://example.com/music".toByteArray()))
        assertNull(NfcMusicTag.parsePayload(ByteArray(10_000)))
        assertFalse(NfcMusicTag.validToken("a45ef725-2ec6-4b65-b178-e05ac3644338".uppercase()))
    }
}
