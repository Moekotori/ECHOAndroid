package app.echo.android

import app.echo.android.data.EchoAppSettings
import app.echo.android.data.JellyfinEndpoint
import app.echo.android.data.smbEndpointFrom
import app.echo.android.data.WebDavEndpoint
import app.echo.android.model.library.EchoTrack

/** Readiness means matching registered credentials, not a speculative network probe. */
internal fun trackVersionSourceReadiness(settings: EchoAppSettings, echoLinkConnected: Boolean): (EchoTrack) -> Boolean {
    val subsonic = subsonicEndpointFrom(settings)?.sourceId
    val jellyfin = settings.jellyfinServerUrl?.takeIf { it.isNotBlank() && !settings.jellyfinAccessToken.isNullOrBlank() }
        ?.let { JellyfinEndpoint(it, settings.jellyfinUsername.orEmpty(), settings.jellyfinPassword.orEmpty()).sourceId }
    val webdav = settings.webDavServerUrl?.takeIf { it.isNotBlank() && !settings.webDavUsername.isNullOrBlank() && !settings.webDavPassword.isNullOrBlank() }
        ?.let { WebDavEndpoint(it, settings.webDavUsername.orEmpty(), settings.webDavPassword.orEmpty()).sourceId }
    val smb = smbEndpointFrom(settings.smbServerUrl, settings.smbUsername, settings.smbPassword)?.sourceId
    return { track -> when {
        track.source.id.startsWith("subsonic:") -> track.source.id == subsonic
        track.source.id.startsWith("jellyfin:") -> track.source.id == jellyfin
        track.source.id.startsWith("webdav:") -> track.source.id == webdav
        track.source.id.startsWith("smb:") -> track.source.id == smb
        track.source.id == "echo-link" -> echoLinkConnected
        else -> false
    } }
}
