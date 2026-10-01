package app.echo.android.playback

import app.echo.android.model.lyrics.EchoLyricDisplaySnapshot

object EchoStatusLyricPolicy {
    fun text(snapshot: EchoLyricDisplaySnapshot, hideTranslation: Boolean): String? {
        if (!snapshot.isPlaying) return null
        val current = snapshot.current ?: return null
        if (current.text.isBlank()) return null
        return EchoNotificationLyricPolicy.clampText(
            if (hideTranslation || current.translation.isNullOrBlank()) current.text
            else "${current.text} · ${current.translation}",
        )
    }

    fun supportsSystemStatusBar(manufacturer: String): Boolean =
        manufacturer.equals("meizu", ignoreCase = true)

    const val FlymeAlwaysShowTicker = 0x01000000
}
