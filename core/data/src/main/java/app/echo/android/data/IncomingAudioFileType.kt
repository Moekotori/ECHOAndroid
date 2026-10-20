package app.echo.android.data

/** Use the scanner's format policy when a file provider reports an opaque binary MIME. */
fun isSupportedIncomingAudioFile(fileName: String, mimeType: String?): Boolean =
    LocalAudioFileTypes.isSupported(fileName, mimeType)

fun incomingAudioMimeType(fileName: String, mimeType: String?): String? =
    LocalAudioFileTypes.resolvedMimeType(fileName, mimeType)
