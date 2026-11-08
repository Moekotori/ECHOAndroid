package app.echo.android

import app.echo.android.data.EchoAppSettings
import app.echo.android.data.JellyfinEndpoint
import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.smbEndpointFrom
import app.echo.android.smb.EchoSmbConnections
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import app.echo.android.playback.EchoJellyfinPlaybackCredential
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry
import app.echo.android.playback.EchoSubsonicPlaybackCredential
import app.echo.android.playback.EchoWebDavPlaybackCredential
import app.echo.android.playback.shouldReplaceRegisteredRemoteCredentials
import java.util.concurrent.atomic.AtomicReference

internal data class RemotePlaybackCredentialSnapshot(
    val webDavServerUrl: String?,
    val webDavUsername: String?,
    val webDavPassword: String?,
    val subsonicServerUrl: String?,
    val subsonicUsername: String?,
    val subsonicPassword: String?,
    val jellyfinServerUrl: String?,
    val jellyfinAccessToken: String?,
    val smbServerUrl: String? = null,
    val smbUsername: String? = null,
    val smbPassword: String? = null,
    val subsonicConnections: List<SubsonicEndpoint> = emptyList(),
)

internal fun EchoAppSettings.remotePlaybackCredentialSnapshot(): RemotePlaybackCredentialSnapshot =
    RemotePlaybackCredentialSnapshot(
        webDavServerUrl = webDavServerUrl,
        webDavUsername = webDavUsername,
        webDavPassword = webDavPassword,
        subsonicServerUrl = subsonicServerUrl,
        subsonicUsername = subsonicUsername,
        subsonicPassword = subsonicPassword,
        jellyfinServerUrl = jellyfinServerUrl,
        jellyfinAccessToken = jellyfinAccessToken,
        smbServerUrl = smbServerUrl,
        smbUsername = smbUsername,
        smbPassword = smbPassword,
        subsonicConnections = subsonicConnections,
    )

internal val EchoSubsonicEndpointRef = AtomicReference<SubsonicEndpoint?>(null)
internal val EchoSubsonicEndpoints = kotlinx.coroutines.flow.MutableStateFlow<List<SubsonicEndpoint>>(emptyList())

internal fun subsonicEndpointForTrack(trackId: String?): SubsonicEndpoint? =
    subsonicEndpointForTrack(trackId, EchoSubsonicEndpoints.value)

internal fun subsonicEndpointForTrack(trackId: String?, endpoints: List<SubsonicEndpoint>): SubsonicEndpoint? {
    if (trackId == null || !trackId.startsWith("subsonic:") || !trackId.contains(":song:")) return null
    val source = trackId.substringBefore(":song:")
    return endpoints.firstOrNull { it.sourceId == source }
}

internal fun applyRemotePlaybackCredentials(
    settings: EchoAppSettings,
    allowClearIfEmpty: Boolean,
) {
    val webDav = listOfNotNull(webDavPlaybackCredential(settings))
    if (
        shouldReplaceRegisteredRemoteCredentials(
            incomingEmpty = webDav.isEmpty(),
            registryAlreadyReady = EchoRemotePlaybackAuthRegistry.hasWebDavCredentials(),
            allowClearIfEmpty = allowClearIfEmpty,
        )
    ) {
        EchoRemotePlaybackAuthRegistry.replaceWebDavCredentials(webDav)
    }
    val endpoints = (listOfNotNull(subsonicEndpointFrom(settings)) + settings.subsonicConnections).distinctBy { it.sourceId }
    val subsonic = endpoints.map { server ->
        EchoSubsonicPlaybackCredential(server.normalizedBaseUrl, server.username, server.password, server.sourceId)
    }
    if (
        shouldReplaceRegisteredRemoteCredentials(
            incomingEmpty = subsonic.isEmpty(),
            registryAlreadyReady = EchoRemotePlaybackAuthRegistry.hasSubsonicCredentials(),
            allowClearIfEmpty = allowClearIfEmpty,
        )
    ) {
        EchoSubsonicEndpoints.value = endpoints
        EchoRemotePlaybackAuthRegistry.replaceSubsonicCredentials(subsonic)
        EchoArtworkUrlRewriteRegistry.notifyChanged()
    }
    val jellyfin = listOfNotNull(jellyfinPlaybackCredential(settings))
    if (
        shouldReplaceRegisteredRemoteCredentials(
            incomingEmpty = jellyfin.isEmpty(),
            registryAlreadyReady = EchoRemotePlaybackAuthRegistry.hasJellyfinCredentials(),
            allowClearIfEmpty = allowClearIfEmpty,
        )
    ) {
        EchoRemotePlaybackAuthRegistry.replaceJellyfinCredentials(jellyfin)
        EchoArtworkUrlRewriteRegistry.notifyChanged()
    }
    val smb = listOfNotNull(smbEndpointFrom(settings.smbServerUrl, settings.smbUsername, settings.smbPassword))
    if (smb.isNotEmpty() || allowClearIfEmpty) {
        EchoSmbConnections.replaceCredentials(smb)
    }
    EchoSubsonicEndpointRef.set(subsonicEndpointFrom(settings))
}

internal fun subsonicEndpointFrom(settings: EchoAppSettings): SubsonicEndpoint? {
    val credential = subsonicPlaybackCredential(settings) ?: return null
    return SubsonicEndpoint(
        baseUrl = credential.baseUrl,
        username = credential.username,
        password = credential.password,
    )
}

private fun webDavPlaybackCredential(settings: EchoAppSettings): EchoWebDavPlaybackCredential? {
    val serverUrl = settings.webDavServerUrl?.takeIf { it.isNotBlank() } ?: return null
    val username = settings.webDavUsername?.takeIf { it.isNotBlank() } ?: return null
    val password = settings.webDavPassword?.takeIf { it.isNotBlank() } ?: return null
    return EchoWebDavPlaybackCredential(
        baseUrl = serverUrl,
        username = username,
        password = password,
    )
}

internal fun jellyfinEndpointFrom(settings: EchoAppSettings): JellyfinEndpoint? {
    val serverUrl = settings.jellyfinServerUrl?.takeIf { it.isNotBlank() } ?: return null
    val username = settings.jellyfinUsername?.takeIf { it.isNotBlank() } ?: return null
    val password = settings.jellyfinPassword?.takeIf { it.isNotBlank() } ?: return null
    return JellyfinEndpoint(
        baseUrl = serverUrl,
        username = username,
        password = password,
        accessToken = settings.jellyfinAccessToken,
        userId = settings.jellyfinUserId,
    )
}

private fun jellyfinPlaybackCredential(settings: EchoAppSettings): EchoJellyfinPlaybackCredential? {
    val serverUrl = settings.jellyfinServerUrl?.takeIf { it.isNotBlank() } ?: return null
    val accessToken = settings.jellyfinAccessToken?.takeIf { it.isNotBlank() } ?: return null
    return EchoJellyfinPlaybackCredential(
        baseUrl = serverUrl,
        accessToken = accessToken,
    )
}

private fun subsonicPlaybackCredential(settings: EchoAppSettings): EchoSubsonicPlaybackCredential? {
    val serverUrl = settings.subsonicServerUrl?.takeIf { it.isNotBlank() } ?: return null
    val username = settings.subsonicUsername?.takeIf { it.isNotBlank() } ?: return null
    val password = settings.subsonicPassword?.takeIf { it.isNotBlank() } ?: return null
    return EchoSubsonicPlaybackCredential(
        baseUrl = serverUrl,
        username = username,
        password = password,
        sourceId = SubsonicEndpoint(serverUrl, username, password).sourceId,
    )
}
