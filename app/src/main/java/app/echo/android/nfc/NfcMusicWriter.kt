package app.echo.android.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

internal enum class NfcWriteFailure { ReadOnly, TooSmall, Unsupported, Changed, Disconnected, Storage }
internal class NfcWriteException(val reason: NfcWriteFailure) : IOException()
internal data class NfcTagInspection(val existing: ByteArray?, val needsFormatting: Boolean = false)

/** All methods run on IO; no NFC hardware handles survive a call. */
internal object NfcMusicWriter {
    fun inspect(tag: Tag, message: NdefMessage): NfcTagInspection {
        val ndef = Ndef.get(tag)
        if (ndef != null) return ndef.use {
            it.connect()
            if (!it.isWritable) throw NfcWriteException(NfcWriteFailure.ReadOnly)
            if (message.toByteArray().size > it.maxSize) throw NfcWriteException(NfcWriteFailure.TooSmall)
            NfcTagInspection(contents(it.ndefMessage))
        }
        if (NdefFormatable.get(tag) == null) throw NfcWriteException(NfcWriteFailure.Unsupported)
        return NfcTagInspection(null, needsFormatting = true)
    }

    fun write(tag: Tag, message: NdefMessage, approvedContent: ByteArray?): Boolean {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.use {
                it.connect()
                if (!it.isWritable) throw NfcWriteException(NfcWriteFailure.ReadOnly)
                if (message.toByteArray().size > it.maxSize) throw NfcWriteException(NfcWriteFailure.TooSmall)
                val current = contents(it.ndefMessage)
                if (!sameContent(current, approvedContent)) throw NfcWriteException(NfcWriteFailure.Changed)
                it.writeNdefMessage(message)
                if (!sameContent(it.ndefMessage?.toByteArray(), message.toByteArray())) throw IOException()
            }
            return true
        } else {
            if (approvedContent != null) throw NfcWriteException(NfcWriteFailure.Changed)
            val formatable = NdefFormatable.get(tag) ?: throw NfcWriteException(NfcWriteFailure.Unsupported)
            formatable.use { it.connect(); it.format(message) }
            // This Tag still has its old technology list. Verify on the next discovery.
            return false
        }
    }

    fun verify(tag: Tag, token: String): Boolean {
        val ndef = Ndef.get(tag) ?: return false
        return ndef.use { it.connect(); NfcMusicTag.token(it.ndefMessage) == token }
    }

    private fun sameContent(a: ByteArray?, b: ByteArray?) =
        if (a == null || b == null) a == null && b == null else a.contentEquals(b)

    private fun contents(message: NdefMessage?): ByteArray? = message?.takeIf {
        it.records.any { record -> record.tnf != NdefRecord.TNF_EMPTY }
    }?.toByteArray()
}
