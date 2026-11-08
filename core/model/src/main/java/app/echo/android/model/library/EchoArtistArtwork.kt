package app.echo.android.model.library

import java.text.Normalizer
import java.util.Locale

data class EchoArtistArtwork(val manualUri: String? = null, val localUri: String? = null, val checkedAt: Long = 0) {
    val preferredUri: String? get() = manualUri ?: localUri
    companion object {
        private val Whitespace = Regex("\\s+")
        fun key(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT).replace(Whitespace, " ")
    }
}
