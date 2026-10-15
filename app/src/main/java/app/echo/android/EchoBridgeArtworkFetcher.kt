package app.echo.android

import android.net.Uri
import app.echo.android.connect.EchoBridgeArtwork
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Dimension
import okio.Buffer

/** Coil owns request cancellation, decoded image sizes and its existing bounded cache. */
internal class EchoBridgeArtworkFetcher(private val uri: Uri, private val options: Options) : Fetcher {
    override suspend fun fetch(): SourceResult {
        val pixels = maxOf((options.size.width as? Dimension.Pixels)?.px ?: 512,
            (options.size.height as? Dimension.Pixels)?.px ?: 512)
        val image = EchoBridgeArtwork.read(uri.toString(), pixels)
            ?: throw java.io.IOException("Bridge artwork is unavailable")
        return SourceResult(source = ImageSource(Buffer().write(image.bytes), options.context),
            mimeType = image.mimeType, dataSource = DataSource.NETWORK)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (EchoBridgeArtwork.handles(data.toString())) EchoBridgeArtworkFetcher(data, options) else null
    }
}
