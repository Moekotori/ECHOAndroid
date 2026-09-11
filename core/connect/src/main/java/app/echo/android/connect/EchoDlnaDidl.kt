package app.echo.android.connect

object EchoDlnaDidl {
    fun build(
        id: String,
        streamUrl: String,
        title: String,
        artist: String,
        album: String? = null,
        artworkUrl: String? = null,
        mimeType: String,
        durationMs: Long = 0L,
    ): String {
        val protocolInfo = protocolInfo(mimeType)
        val duration = durationMs.takeIf { it > 0L }?.let { " duration=\"${EchoDlnaSoap.formatDlnaTime(it)}\"" }.orEmpty()
        val albumTag = album?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "<upnp:album>${EchoDlnaSoap.escapeXml(it)}</upnp:album>" }
            .orEmpty()
        val artTag = artworkUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "<upnp:albumArtURI dlna:profileID=\"JPEG_TN\">${EchoDlnaSoap.escapeXml(it)}</upnp:albumArtURI>" }
            .orEmpty()
        return buildString {
            append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"")
            append(" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"")
            append(" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"")
            append(" xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\">")
            append("<item id=\"").append(EchoDlnaSoap.escapeXml(id)).append("\" parentID=\"0\" restricted=\"1\">")
            append("<dc:title>").append(EchoDlnaSoap.escapeXml(title)).append("</dc:title>")
            append("<upnp:artist>").append(EchoDlnaSoap.escapeXml(artist)).append("</upnp:artist>")
            append(albumTag)
            append(artTag)
            append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
            append("<res protocolInfo=\"").append(EchoDlnaSoap.escapeXml(protocolInfo)).append('"')
            append(duration).append('>')
            append(EchoDlnaSoap.escapeXml(streamUrl))
            append("</res></item></DIDL-Lite>")
        }
    }

    fun protocolInfo(mimeType: String): String {
        val mime = mimeType.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
        val profile = when (normalizeMime(mime)) {
            "audio/mpeg" -> "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            "audio/wav" -> "DLNA.ORG_PN=WAV;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            "audio/flac" -> "DLNA.ORG_PN=FLAC;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            "audio/x-dsf" -> "DLNA.ORG_PN=DSF;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            "audio/x-dff" -> "DLNA.ORG_PN=DFF;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            else -> "DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        }
        return "http-get:*:$mime:$profile"
    }

    fun normalizeMime(mimeType: String): String =
        when (val raw = mimeType.substringBefore(';').trim().lowercase()) {
            "audio/x-flac", "application/flac" -> "audio/flac"
            "audio/x-wav", "audio/wave" -> "audio/wav"
            "audio/mp3" -> "audio/mpeg"
            "audio/dsf", "audio/x-dsd-dsf" -> "audio/x-dsf"
            "audio/dff", "audio/x-dsd-dff" -> "audio/x-dff"
            "audio/dsd" -> "audio/x-dsd"
            else -> raw
        }
}
