package app.echo.android.data

/** Use the original Unicode filename when a provider supplies no usable title. */
internal fun localAudioTitle(tag: String?, fileName: String?): String =
    tag.takeUnlessUnknownMetadata()
        ?: fileName?.substringBeforeLast('.', fileName)?.takeIf { it.isNotBlank() }
        ?: UnknownTrackTitle
