package app.echo.android.model.playback

/** A format reset must not raise volume or discard device-specific playback permissions. */
fun EchoUsbOutputOptions.resetFormatAndBuffer(): EchoUsbOutputOptions = copy(
    sampleRateHz = 0,
    bitDepth = 0,
    channelCount = 0,
    bufferMode = EchoUsbBufferMode.Balanced,
)
