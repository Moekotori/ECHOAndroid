package app.echo.android.design

import app.echo.android.model.settings.EchoEffectivePerformanceMode

val EchoEffectivePerformanceMode.backgroundMaxBlur: Float
    get() = when (this) {
        EchoEffectivePerformanceMode.Lightweight -> 4f
        EchoEffectivePerformanceMode.Balanced -> 16f
        EchoEffectivePerformanceMode.HighPerformance -> 28f
    }
