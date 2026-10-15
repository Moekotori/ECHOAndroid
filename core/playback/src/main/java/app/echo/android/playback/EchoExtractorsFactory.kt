package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.datasource.DataSource
import android.net.Uri

@UnstableApi
internal class EchoExtractorsFactory(private val seekSources: DataSource.Factory? = null) : ExtractorsFactory {
    private val defaults = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> = create(null, defaults.createExtractors())

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        create(uri, defaults.createExtractors(uri, responseHeaders))

    private fun create(uri: Uri?, fallback: Array<Extractor>): Array<Extractor> =
        arrayOf(EchoFfmpegExtractor(uri, seekSources), EchoApeExtractor(), EchoDsfExtractor(), EchoDffExtractor(), *fallback)
}
