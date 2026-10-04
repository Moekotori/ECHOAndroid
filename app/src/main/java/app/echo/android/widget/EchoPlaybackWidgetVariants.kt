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
