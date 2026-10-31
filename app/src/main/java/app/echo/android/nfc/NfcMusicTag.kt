package app.echo.android.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.UUID

/** Versioned, bounded payload. Contains neither catalog IDs nor file/network addresses. */
internal object NfcMusicTag {
    const val Mime = "application/vnd.app.echo.android.music"
    private const val Prefix = "echo-music:1:"
    private val tokenPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    fun newToken(): String = UUID.randomUUID().toString()
    fun validToken(token: String) = tokenPattern.matches(token)
    fun payload(token: String): ByteArray {
        require(validToken(token))
        return (Prefix + token).toByteArray(US_ASCII)
    }
    fun parsePayload(payload: ByteArray): String? {
        if (payload.size != Prefix.length + 36) return null
        val raw = String(payload, US_ASCII)
        return raw.takeIf { it.startsWith(Prefix) }?.removePrefix(Prefix)?.takeIf(::validToken)
    }
    fun message(token: String) = NdefMessage(NdefRecord.createMime(Mime, payload(token)))
    fun token(message: NdefMessage?): String? {
        val record = message?.records?.singleOrNull() ?: return null
        if (record.tnf != NdefRecord.TNF_MIME_MEDIA || String(record.type, US_ASCII) != Mime) return null
        return parsePayload(record.payload)
    }
    @Suppress("DEPRECATION")
    fun request(intent: Intent): String? {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED || intent.type != Mime) return null
        return runCatching {
            val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            token(messages?.singleOrNull() as? NdefMessage)
        }.getOrNull()
    }
}
