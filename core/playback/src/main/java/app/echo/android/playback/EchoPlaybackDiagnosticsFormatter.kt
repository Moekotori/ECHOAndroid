package app.echo.android.playback

fun EchoPlaybackDiagnostics.toReadableLines(): List<String> = buildList {
    add("route=$outputRoute")
    add("offload=$offloadActive")
    add("bufferedMs=$bufferedMs")
    add("requestToken=$requestToken")
    codec?.let { add("codec=$it") }
    sampleRateHz?.let { add("sampleRateHz=$it") }
    channelCount?.let { add("channelCount=$it") }
    bitrate?.let { add("bitrate=$it") }
    lastCommand?.let { add("lastCommand=$it") }
    lastError?.let { add("lastError=${it.kind}:${it.message}") }
}
