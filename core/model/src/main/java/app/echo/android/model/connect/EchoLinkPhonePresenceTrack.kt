package app.echo.android.model.connect

data class EchoLinkPhonePresenceTrack(val title: String, val artist: String) {
    companion object {
        private const val MaxTextLength = 256

        fun fromMetadata(title: String, artist: String): EchoLinkPhonePresenceTrack? {
            val displayTitle = title.toPresenceText()
            return if (displayTitle.isEmpty()) null
            else EchoLinkPhonePresenceTrack(displayTitle, artist.toPresenceText())
        }

        private fun String.toPresenceText(): String {
            val text = trim()
            if (text.length <= MaxTextLength) return text
            val end = if (text[MaxTextLength - 1].isHighSurrogate()) MaxTextLength - 1 else MaxTextLength
            return text.substring(0, end).trimEnd()
        }
    }
}
