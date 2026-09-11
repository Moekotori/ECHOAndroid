package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory

@UnstableApi
internal class EchoExtractorsFactory : ExtractorsFactory {
    private val defaults = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> {
        val fallback = defaults.createExtractors()
        return arrayOf(EchoDsfExtractor(), EchoDffExtractor(), *fallback)
    }
}
