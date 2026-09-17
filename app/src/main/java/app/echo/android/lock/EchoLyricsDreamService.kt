package app.echo.android.lock

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.media3.common.util.UnstableApi
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication

@UnstableApi
class EchoLyricsDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(
            ComposeView(wrapEchoAppLocaleToMatchApplication()).apply {
                setContent { EchoLockLyricsHost() }
            },
        )
    }
}
