@file:OptIn(UnstableApi::class)

package app.echo.android.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.layout.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.media3.common.util.UnstableApi
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlin.math.roundToInt

class EchoPlaybackWidget : EchoPlaybackSurfaceWidget(EchoWidgetStyle.Player)

/** System widget providers share one passive snapshot and one bounded artwork cache. */
abstract class EchoPlaybackSurfaceWidget internal constructor(internal val style: EchoWidgetStyle) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        when (style) {
            EchoWidgetStyle.Player -> setOf(
                DpSize(EchoPlaybackWidgetLayout.CompactWidthDp.dp, EchoPlaybackWidgetLayout.CompactHeightDp.dp),
                DpSize(EchoPlaybackWidgetLayout.ExpandedWidthDp.dp, EchoPlaybackWidgetLayout.ExpandedHeightDp.dp),
                DpSize(EchoPlaybackWidgetLayout.WideWidthDp.dp, EchoPlaybackWidgetLayout.WideHeightDp.dp),
                DpSize(EchoPlaybackWidgetLayout.TallWidthDp.dp, EchoPlaybackWidgetLayout.TallHeightDp.dp),
                DpSize(EchoPlaybackWidgetLayout.SquareWidthDp.dp, EchoPlaybackWidgetLayout.SquareHeightDp.dp),
                DpSize(300.dp, 300.dp),
            )
            EchoWidgetStyle.Mini -> setOf(DpSize(180.dp, 56.dp), DpSize(300.dp, 56.dp))
            EchoWidgetStyle.Cover -> setOf(DpSize(140.dp, 140.dp), DpSize(220.dp, 220.dp), DpSize(280.dp, 280.dp))
            EchoWidgetStyle.Lyrics -> setOf(DpSize(250.dp, 140.dp), DpSize(300.dp, 180.dp), DpSize(300.dp, 260.dp))
        },
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val localized = context.wrapEchoAppLocaleToMatchApplication()
        val snapshot = readWidgetPlaybackSnapshot(localized)
        val live = EchoPlaybackProcessRuntime.surfaceSnapshot
        val lyricLine = EchoPlaybackProcessRuntime.notificationLyricLine.value
            .takeIf { live.hasTrack && live.mediaId == snapshot.mediaId }
        val options = app.echo.android.data.EchoWidgetOptionsStore(localized).read(
            androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id))
        val artwork = if (options.showArtwork) EchoWidgetArtwork.load(localized, snapshot, allowRemote = live.hasTrack) else null
        provideContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalWidgetOptions provides options) {
              androidx.glance.GlanceTheme {
                Column(androidx.glance.GlanceModifier.fillMaxSize()) {
                    Box(androidx.glance.GlanceModifier.fillMaxWidth().defaultWeight()) {
                        val size = LocalSize.current
                        val contentHeight = size.height.value - if (options.playlistMediaId != null && size.height.value >= 120) 48 else 0
                        when (style) {
                            EchoWidgetStyle.Player, EchoWidgetStyle.Mini -> EchoPlaybackWidgetContent(
                                context = localized,
                                snapshot = snapshot,
                                artwork = artwork,
                                availableHeightDp = contentHeight,
                                lyricLine = if (style == EchoWidgetStyle.Player && options.showLyrics) lyricLine else null,
                                mode = if (style == EchoWidgetStyle.Mini) {
                                    if (size.width.value >= 300) EchoPlaybackWidgetMode.Wide else EchoPlaybackWidgetMode.Compact
                                } else EchoPlaybackWidgetLayout.mode(size.width.value.roundToInt(), contentHeight.roundToInt()),
                            )
                            EchoWidgetStyle.Cover -> EchoCoverWidgetContent(localized, snapshot, artwork)
                            EchoWidgetStyle.Lyrics -> EchoLyricsWidgetContent(localized, snapshot, artwork, lyricLine.takeIf { options.showLyrics }, contentHeight)
                        }
                    }
                    WidgetBoundPlaylist(localized, options)
                }
              }
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        app.echo.android.data.EchoWidgetOptionsStore(context).remove(
            androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}

class EchoPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoPlaybackWidget()
}
