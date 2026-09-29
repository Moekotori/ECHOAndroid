package app.echo.android.data

import app.echo.android.smb.EchoSmbEndpoint
import app.echo.android.smb.EchoSmbFileEntry
import app.echo.android.smb.EchoSmbPaths
import app.echo.android.smb.EchoSmbScanner

/**
 * 由设置里的「地址 / 用户名 / 密码」得到 SMB 端点。地址形如 `smb://nas/Music/子目录`；
 * 用户名可写成 `DOMAIN\user`；用户名留空表示访客。地址无法识别时返回 null。
 */
fun smbEndpointFrom(serverUrl: String?, username: String?, password: String?): EchoSmbEndpoint? {
    val address = EchoSmbEndpoint.parseAddress(serverUrl ?: return null) ?: return null
    val rawUser = username?.trim().orEmpty()
    val domain = if ('\\' in rawUser) rawUser.substringBefore('\\') else ""
    val user = if ('\\' in rawUser) rawUser.substringAfter('\\') else rawUser
    return EchoSmbEndpoint(
        host = address.host,
        port = address.port,
        share = address.share,
        basePath = address.basePath,
        username = user,
        password = if (user.isBlank()) "" else password.orEmpty(),
        domain = domain,
    )
}

/** 与 WebDAV 一致：不读文件标签，标题取文件名，专辑取上级目录名。 */
internal fun EchoSmbFileEntry.toLibraryTrackEntity(
    endpoint: EchoSmbEndpoint,
    scanRunId: Long,
): LibraryTrackEntity {
    val uri = EchoSmbPaths.uri(endpoint.normalizedHost, endpoint.port, endpoint.normalizedShare, path)
    val relative = path.removePrefix(endpoint.normalizedBasePath).trim('/')
    val title = name.substringBeforeLast('.', missingDelimiterValue = name)
    val album = relative.substringBeforeLast('/', missingDelimiterValue = "")
        .substringAfterLast('/')
        .ifBlank { endpoint.normalizedShare }
    return LibraryTrackEntity(
        id = "${endpoint.sourceId}:file:${smbStableHash(uri)}",
        contentUri = uri,
        title = title,
        artist = SmbArtistPlaceholder,
        album = album,
        albumArtist = SmbArtistPlaceholder,
        artworkUri = null,
        durationMs = 0L,
        trackNumber = null,
        discNumber = null,
        year = null,
        mimeType = LocalAudioFileTypes.resolvedMimeType(name, null),
        sizeBytes = sizeBytes,
        sampleRateHz = null,
        dateModifiedSeconds = modifiedEpochMs / 1000L,
        source = endpoint.sourceId,
        relativePath = relative.substringBeforeLast('/', missingDelimiterValue = ""),
        lastSeenScanRunId = scanRunId,
    ).withScanMetadata(scanRunId)
}

internal suspend fun EchoSmbScanner.scanAudio(
    endpoint: EchoSmbEndpoint,
    onFile: suspend (EchoSmbFileEntry) -> Unit,
): RemoteSyncVisit {
    val result = scan(
        endpoint = endpoint,
        accept = { name -> LocalAudioFileTypes.isSupported(name, null) },
        onFile = onFile,
    )
    return RemoteSyncVisit(visitedCount = result.fileCount, hitVisitCap = result.hitLimit)
}

private const val SmbArtistPlaceholder = "SMB"

private fun smbStableHash(value: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.take(10).joinToString("") { "%02x".format(it) }
}
