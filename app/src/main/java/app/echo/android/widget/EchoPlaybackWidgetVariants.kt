package app.echo.android.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class EchoMiniPlaybackWidget : EchoPlaybackSurfaceWidget(EchoWidgetStyle.Mini)

class EchoMiniPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoMiniPlaybackWidget()
}

class EchoCoverPlaybackWidget : EchoPlaybackSurfaceWidget(EchoWidgetStyle.Cover)

class EchoCoverPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoCoverPlaybackWidget()
}

class EchoLyricsPlaybackWidget : EchoPlaybackSurfaceWidget(EchoWidgetStyle.Lyrics)

class EchoLyricsPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoLyricsPlaybackWidget()
}

class EchoRecordPlaybackWidget : EchoPlaybackSurfaceWidget(EchoWidgetStyle.Record)

class EchoRecordPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoRecordPlaybackWidget()
}

class EchoBannerPlaybackWidget : EchoPlaybackSurfaceWidget(EchoWidgetStyle.Banner)

class EchoBannerPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoBannerPlaybackWidget()
}

class EchoTypographyPlaybackWidget : EchoPlaybackSurfaceWidget(EchoWidgetStyle.Typography)

class EchoTypographyPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoTypographyPlaybackWidget()
}
