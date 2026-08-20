package app.echo.android.data

import app.echo.android.model.library.LibrarySource
import java.io.StringReader
import java.net.URI
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class WebDavEndpoint(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    val normalizedBaseUrl: String =
        baseUrl.trim().trimEnd('/')

    val sourceId: String =
        "${LibrarySource.WebDav.id}:${webDavStableHash("${normalizedBaseUrl.lowercase(Locale.ROOT)}|${username.trim()}")}"
}

internal data class WebDavAudioFile(
    val id: String,
    val url: String,
    val title: String,
    val album: String,
    val artist: String,
    val path: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedSeconds: Long,
)

internal class WebDavClient(
    private val endpoint: WebDavEndpoint,
    private val client: OkHttpClient = SharedWebDavHttpClient,
) {
    suspend fun scanAudioFiles(
        maxFolders: Int = MaxFoldersPerSync,
        maxTracks: Int = MaxTracksPerSync,
        onFile: suspend (WebDavAudioFile) -> Unit,
    ): RemoteSyncVisit {
        val root = URI(endpoint.normalizedBaseUrl)
        val queue = ArrayDeque<URI>()
        val visited = HashSet<String>()
        queue += root
        var folderCount = 0
        var trackCount = 0
        var hitVisitCap = false

        while (queue.isNotEmpty()) {
            if (folderCount >= maxFolders || trackCount >= maxTracks) {
                hitVisitCap = true
                break
            }
            val folder = queue.removeFirst()
            val folderKey = folder.normalize().toString()
            if (!visited.add(folderKey)) continue
            folderCount += 1

            for (entry in propfind(folder)) {
                if (entry.href.normalize().toString() == folderKey) continue
                if (entry.isDirectory) {
                    if (folderCount + queue.size >= maxFolders) {
                        hitVisitCap = true
                    }
                    queue += entry.href
                } else if (entry.href.isSupportedAudio()) {
                    onFile(entry.toAudioFile(root))
                    trackCount += 1
                    if (trackCount >= maxTracks) {
                        hitVisitCap = true
                        break
                    }
                }
            }
        }
        return RemoteSyncVisit(visitedCount = trackCount, hitVisitCap = hitVisitCap)
    }

    private fun propfind(uri: URI): List<WebDavEntry> {
        val request = Request.Builder()
            .url(uri.toString())
            .header("Depth", "1")
            .header("Authorization", webDavBasicAuthorization(endpoint.username.trim(), endpoint.password))
            .method("PROPFIND", PropfindBody)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("WebDAV 读取失败：HTTP ${response.code}")
            val body = response.body?.string() ?: return emptyList()
            return parseWebDavEntries(base = uri, xml = body)
        }
    }

    private fun WebDavEntry.toAudioFile(root: URI): WebDavAudioFile {
        val fullUrl = href.toString()
        val path = root.relativize(href).toString().trim('/')
        val fileName = href.path.substringAfterLast('/').ifBlank { path.substringAfterLast('/') }
        val title = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val album = path.substringBeforeLast('/', missingDelimiterValue = "")
            .trim('/')
            .substringAfterLast('/')
            .ifBlank { "WebDAV" }
        return WebDavAudioFile(
            id = "${endpoint.sourceId}:file:${webDavStableHash(fullUrl)}",
            url = fullUrl,
            title = title,
            album = album,
            artist = "WebDAV",
            path = path,
            mimeType = contentType ?: href.audioMimeType(),
            sizeBytes = contentLength,
            modifiedSeconds = lastModifiedSeconds,
        )
    }

    private companion object {
        const val MaxFoldersPerSync = 1_000
        const val MaxTracksPerSync = 5_000
    }
}

internal fun WebDavAudioFile.toLibraryTrackEntity(
    endpoint: WebDavEndpoint,
    scanRunId: Long,
): LibraryTrackEntity =
    LibraryTrackEntity(
        id = id,
        contentUri = url,
        title = title,
        artist = artist,
        album = album,
        albumArtist = artist,
        artworkUri = null,
        durationMs = 0L,
        trackNumber = null,
        discNumber = null,
        year = null,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sampleRateHz = null,
        dateModifiedSeconds = modifiedSeconds,
        source = endpoint.sourceId,
        relativePath = path.substringBeforeLast('/', missingDelimiterValue = ""),
        lastSeenScanRunId = scanRunId,
    ).withScanMetadata(scanRunId)

internal data class WebDavEntry(
    val href: URI,
    val isDirectory: Boolean,
    val contentType: String?,
    val contentLength: Long,
    val lastModifiedSeconds: Long,
)

private val SharedWebDavHttpClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

private val PropfindBody =
    """
    <?xml version="1.0" encoding="utf-8" ?>
    <D:propfind xmlns:D="DAV:">
      <D:prop>
        <D:resourcetype/>
        <D:getcontentlength/>
        <D:getcontenttype/>
        <D:getlastmodified/>
      </D:prop>
    </D:propfind>
    """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

internal fun parseWebDavEntries(base: URI, xml: String): List<WebDavEntry> {
    val document = DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        .newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))
    val responses = document.getElementsByTagNameNS("*", "response")
    val entries = ArrayList<WebDavEntry>(responses.length)
    for (index in 0 until responses.length) {
        val response = responses.item(index) as? Element ?: continue
        val hrefText = response.childText("href") ?: continue
        val href = runCatching { base.resolve(hrefText) }.getOrNull() ?: continue
        val propstats = response.getElementsByTagNameNS("*", "propstat")
        val successfulProps = if (propstats.length <= 0) {
            listOf(response)
        } else {
            buildList {
                for (propstatIndex in 0 until propstats.length) {
                    val propstat = propstats.item(propstatIndex) as? Element ?: continue
                    if (!propstat.childText("status").isSuccessfulWebDavStatus()) continue
                    val prop = propstat.getElementsByTagNameNS("*", "prop").item(0) as? Element ?: continue
                    add(prop)
                }
            }
        }
        if (successfulProps.isEmpty()) continue
        val directory = successfulProps.any { prop ->
            val resourcetype = prop.getElementsByTagNameNS("*", "resourcetype").item(0) as? Element
            (resourcetype?.getElementsByTagNameNS("*", "collection")?.length ?: 0) > 0
        } ||
            href.path.endsWith("/")
        entries += WebDavEntry(
            href = href,
            isDirectory = directory,
            contentType = successfulProps.firstNotNullOfOrNull { it.childText("getcontenttype") },
            contentLength = successfulProps.firstNotNullOfOrNull { it.childText("getcontentlength")?.toLongOrNull() } ?: 0L,
            lastModifiedSeconds = successfulProps.firstNotNullOfOrNull {
                it.childText("getlastmodified")?.parseHttpDateSeconds()
            } ?: 0L,
        )
    }
    return entries
}

private fun String?.isSuccessfulWebDavStatus(): Boolean {
    if (this == null) return true
    val statusCode = trim().split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
    return statusCode in 200..299
}

private fun Element.childText(localName: String): String? {
    val nodes = getElementsByTagNameNS("*", localName)
    if (nodes.length <= 0) return null
    return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
}

private fun URI.isSupportedAudio(): Boolean =
    audioMimeType() != null

private fun URI.audioMimeType(): String? {
    val name = path.substringAfterLast('/').lowercase(Locale.ROOT)
    return when {
        name.endsWith(".flac") -> "audio/flac"
        name.endsWith(".mp3") -> "audio/mpeg"
        name.endsWith(".m4a") || name.endsWith(".mp4") -> "audio/mp4"
        name.endsWith(".aac") -> "audio/aac"
        name.endsWith(".ogg") || name.endsWith(".oga") -> "audio/ogg"
        name.endsWith(".opus") -> "audio/opus"
        name.endsWith(".wav") -> "audio/wav"
        name.endsWith(".aiff") || name.endsWith(".aif") -> "audio/aiff"
        name.endsWith(".ape") -> "audio/ape"
        else -> null
    }
}

private fun String.parseHttpDateSeconds(): Long? =
    runCatching {
        OffsetDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
    }.getOrNull()

private fun webDavStableHash(value: String): String =
    value.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }.toString(36)

internal fun webDavBasicAuthorization(username: String, password: String): String {
    val raw = "$username:$password"
    val encoded = java.util.Base64.getEncoder().encodeToString(raw.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
    return "Basic $encoded"
}
