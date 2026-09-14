package app.echo.android.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import app.echo.android.model.radio.EchoRadioStation

/** Radio playlists and segments always go upstream, never through the song disk cache. */
@UnstableApi
internal class EchoRadioMediaSourceFactory(context: Context) : MediaSource.Factory {
    private val songs = DefaultMediaSourceFactory(echoPlaybackDataSourceFactory(context), EchoExtractorsFactory())
    private val radio = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context.applicationContext,
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("ECHOAndroid"),
        ),
        EchoExtractorsFactory(),
    ).setLoadErrorHandlingPolicy(EchoRadioLoadErrorPolicy())

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        if (EchoRadioStation.isRadio(mediaItem.mediaId)) RadioSource(radio.createMediaSource(mediaItem))
        else songs.createMediaSource(mediaItem)

    override fun getSupportedTypes(): IntArray = songs.supportedTypes

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        songs.setDrmSessionManagerProvider(provider)
        radio.setDrmSessionManagerProvider(provider)
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        songs.setLoadErrorHandlingPolicy(policy)
    }

    private class RadioSource(source: MediaSource) : WrappingMediaSource(source) {
        override fun getInitialTimeline(): Timeline? = super.getInitialTimeline()?.let(::RadioTimeline)
        override fun onChildSourceInfoRefreshed(timeline: Timeline) {
            refreshSourceInfo(RadioTimeline(timeline))
        }
    }

    // Preserve HLS timing/default position for live-edge loading; remove DVR controls.
    private class RadioTimeline(timeline: Timeline) : ForwardingTimeline(timeline) {
        override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window =
            super.getWindow(windowIndex, window, defaultPositionProjectionUs).also { it.isSeekable = false }
    }
}

@UnstableApi
internal class EchoRadioLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(3) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (loadErrorInfo.errorCount >= 3) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
}
