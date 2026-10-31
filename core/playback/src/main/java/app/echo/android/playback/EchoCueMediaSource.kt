package app.echo.android.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.TrackOutput
import app.echo.android.model.library.CueSheetPolicy

internal const val EchoCueMime = "audio/x-echo-cue"

@UnstableApi
internal fun MediaItem.cueClip(): EchoCueClip? {
    if (!CueSheetPolicy.isCueTrackId(mediaId)) return null
    val uri = localConfiguration?.uri?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val start = clippingConfiguration.startPositionUs
    val end = clippingConfiguration.endPositionUs.takeUnless { it == C.TIME_END_OF_SOURCE } ?: 0L
    if (start < 0 || end < 0 || (end > 0 && end <= start) || (start == 0L && end == 0L)) return null
    return EchoCueClip(mediaId, CueSheetPolicy.playbackUri(uri), start, end)
}

@UnstableApi
internal data class EchoCueFormat(val clip: EchoCueClip, val source: Format)

@UnstableApi
internal fun Format.cueInputFormat(): Format = (customData as? EchoCueFormat)?.let {
    it.source.buildUpon().setCustomData(it.clip).setEncoderDelay(encoderDelay).setEncoderPadding(encoderPadding).build()
} ?: this

/** Keep preroll blocks in Media3's SampleQueue until the decoder can trim their valid PCM suffix. */
@UnstableApi
internal class EchoCueExtractorsFactory(private val source: ExtractorsFactory, private val clip: EchoCueClip) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = wrap(source.createExtractors())
    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        wrap(source.createExtractors(uri, responseHeaders))

    private fun wrap(extractors: Array<Extractor>): Array<Extractor> = extractors.map { extractor ->
        object : Extractor by extractor {
            override fun init(output: ExtractorOutput) {
                extractor.init(object : ExtractorOutput by output {
                    override fun track(id: Int, type: Int): TrackOutput {
                        val track = output.track(id, type)
                        if (type != C.TRACK_TYPE_AUDIO) return track
                        return object : TrackOutput by track {
                            override fun format(format: Format) {
                                // Standard audio MIME types let SampleQueue drop whole blocks before a seek.
                                // CUE needs the block crossing the cut; the dedicated renderer restores the MIME.
                                // containerMimeType survives MediaSession serialization; customData does not.
                                track.format(format.buildUpon().setSampleMimeType(EchoCueMime).setContainerMimeType(format.sampleMimeType)
                                    .setCustomData(EchoCueFormat(clip, format)).build())
                            }
                        }
                    }
                })
            }
        }
    }.toTypedArray()
}
