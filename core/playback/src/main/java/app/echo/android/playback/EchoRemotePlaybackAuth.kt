package app.echo.android.playback

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

data class EchoWebDavPlaybackCredential(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    val normalizedBaseUrl: String =
        baseUrl.trim().trimEnd('/')

    val authorizationHeader: String =
        basicAuthorization(username.trim(), password)
}

data class EchoSubsonicPlaybackCredential(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    val normalizedBaseUrl: String =
        baseUrl.trim().trimEnd('/')
}

object EchoRemotePlaybackAuthRegistry {
    private val webDavCredentials = AtomicReference<List<EchoWebDavPlaybackCredential>>(emptyList())
    private val subsonicCredentials = AtomicReference<List<EchoSubsonicPlaybackCredential>>(emptyList())

    fun replaceWebDavCredentials(credentials: List<EchoWebDavPlaybackCredential>) {
        webDavCredentials.set(
            credentials
                .filter { it.normalizedBaseUrl.isNotBlank() && it.username.isNotBlank() && it.password.isNotBlank() }
                .distinctBy { it.normalizedBaseUrl },
        )
    }

    fun replaceSubsonicCredentials(credentials: List<EchoSubsonicPlaybackCredential>) {
        subsonicCredentials.set(
            credentials
                .filter { it.normalizedBaseUrl.isNotBlank() && it.username.isNotBlank() && it.password.isNotBlank() }
                .distinctBy { it.normalizedBaseUrl },
        )
    }

    fun isWebDavAuthReadyForUris(uris: Iterable<String>): Boolean =
        webDavAuthReadyForQueue(
            uris = uris,
            credentialBaseUrls = webDavCredentials.get().map { it.normalizedBaseUrl },
        )

    fun hasWebDavCredentials(): Boolean = webDavCredentials.get().isNotEmpty()

    fun hasSubsonicCredentials(): Boolean = subsonicCredentials.get().isNotEmpty()

    fun isSubsonicAuthReadyForUris(uris: Iterable<String>): Boolean =
        subsonicAuthReadyForQueue(
            uris = uris,
            credentialBaseUrls = subsonicCredentials.get().map { it.normalizedBaseUrl },
        )

    @UnstableApi
    internal fun resolve(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        val signedSubsonicUrl = resolveSubsonicUrl(uri.toString())
        if (signedSubsonicUrl != uri.toString()) {
            return dataSpec.buildUpon()
                .setUri(Uri.parse(signedSubsonicUrl))
                .build()
        }

        val userInfo = uri.userInfoDecoded()
        val matchedCredential = matchingWebDavCredential(uri)
        val authorization = when {
            matchedCredential != null -> matchedCredential.authorizationHeader
            !userInfo.isNullOrBlank() -> basicAuthorizationFromUserInfo(userInfo)
            else -> null
        } ?: return dataSpec

        val cleanUri = uri.withoutUserInfo()
        val headers = LinkedHashMap(dataSpec.httpRequestHeaders)
        headers["Authorization"] = authorization
        return dataSpec.buildUpon()
            .setUri(cleanUri)
            .setHttpRequestHeaders(headers)
            .build()
    }

    fun resolveSubsonicUrl(url: String): String {
        val credential = matchingSubsonicCredential(url) ?: return url
        return applySubsonicTokenAuth(url, credential)
    }

    internal fun cacheIdentity(uri: Uri, requestHeaders: Map<String, String>): String =
        cacheIdentity(uri.toString(), requestHeaders)

    internal fun cacheIdentity(url: String, requestHeaders: Map<String, String>): String {
        val subsonicCredential = matchingSubsonicCredential(url)
        val webDavCredential = matchingWebDavCredentialForUrl(url)
        val credentialIdentity = subsonicCredential?.let { credential ->
            "subsonic:${credential.normalizedBaseUrl}:${credential.username.trim()}"
        } ?: webDavCredential?.let { credential ->
            "${credential.normalizedBaseUrl}:${credential.username.trim()}"
        }
        val userInfoIdentity = if (credentialIdentity == null) {
            userInfoFromUrl(url)?.takeIf { it.isNotBlank() }?.let { info ->
                "${hostFromUrl(url).orEmpty()}:$info"
            }
        } else {
            null
        }
        val authorizationHeaders = requestHeaders.entries
            .filter { it.key.equals("Authorization", ignoreCase = true) }
            .map { it.value }
        val sensitiveQueryValues = if (subsonicCredential != null) {
            emptyList()
        } else {
            parseQueryParameters(url)
                .filter { it.first.lowercase() in sensitiveCacheQueryNames }
                .sortedWith(compareBy<Pair<String, String>> { it.first.lowercase() }.thenBy { it.second })
        }
        return remotePlaybackCacheNamespace(
            credentialIdentity = credentialIdentity,
            userInfoIdentity = userInfoIdentity,
            authorizationHeaders = authorizationHeaders,
            sensitiveQueryValues = sensitiveQueryValues,
        )
    }

    private fun matchingWebDavCredential(uri: Uri): EchoWebDavPlaybackCredential? =
        matchingWebDavCredentialForUrl(uri.withoutUserInfo().toString())

    private fun matchingWebDavCredentialForUrl(url: String): EchoWebDavPlaybackCredential? {
        val cleanUrl = stripUserInfo(url)
        return webDavCredentials.get()
            .firstOrNull { credential ->
                cleanUrl == credential.normalizedBaseUrl ||
                    cleanUrl.startsWith("${credential.normalizedBaseUrl}/")
            }
    }

    private fun matchingSubsonicCredential(url: String): EchoSubsonicPlaybackCredential? {
        if (!isSubsonicRestUrl(url)) return null
        val cleanUrl = stripUserInfo(url)
        return subsonicCredentials.get()
            .firstOrNull { credential ->
                cleanUrl == credential.normalizedBaseUrl ||
                    cleanUrl.startsWith("${credential.normalizedBaseUrl}/")
            }
    }
}

@UnstableApi
fun echoPlaybackDataSourceFactory(context: Context): DataSource.Factory =
    EchoPlaybackDataSourceFactory(context.applicationContext)

@UnstableApi
fun echoRemoteAuthDataSourceFactory(context: Context): ResolvingDataSource.Factory =
    ResolvingDataSource.Factory(
        DefaultDataSource.Factory(context),
        echoRemoteAuthResolver(),
    )

@UnstableApi
private fun echoRemoteAuthResolver(): ResolvingDataSource.Resolver =
    ResolvingDataSource.Resolver { dataSpec -> EchoRemotePlaybackAuthRegistry.resolve(dataSpec) }

@UnstableApi
private class EchoPlaybackDataSourceFactory(
    private val context: Context,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val resolvingFactory = echoRemoteAuthDataSourceFactory(context)
        val remoteCacheDataSource = CacheDataSource.Factory()
            .setCache(EchoRemotePlaybackCache.get(context))
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setCacheKeyFactory(EchoRemotePlaybackCacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()

        return EchoSchemeRoutingDataSource(
            remoteDataSource = remoteCacheDataSource,
            localDataSource = resolvingFactory.createDataSource(),
        )
    }
}

@UnstableApi
private class EchoSchemeRoutingDataSource(
    private val remoteDataSource: DataSource,
    private val localDataSource: DataSource,
) : DataSource {
    private var activeDataSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        remoteDataSource.addTransferListener(transferListener)
        localDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        activeDataSource = if (dataSpec.uri.isRemotePlaybackUri()) {
            remoteDataSource
        } else {
            localDataSource
        }
        return requireNotNull(activeDataSource).open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        activeDataSource?.read(buffer, offset, length)
            ?: throw IOException("DataSource is not open.")

    override fun getUri(): Uri? =
        activeDataSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        activeDataSource?.responseHeaders.orEmpty()

    override fun close() {
        try {
            activeDataSource?.close()
        } finally {
            activeDataSource = null
        }
    }
}

@UnstableApi
private object EchoRemotePlaybackCache {
    @Volatile
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache =
        cache ?: SimpleCache(
            File(context.cacheDir, "echo-remote-playback-cache"),
            EchoPlaybackCacheEvictor(),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
}

@UnstableApi
private object EchoRemotePlaybackCacheKeyFactory : CacheKeyFactory {
    override fun buildCacheKey(dataSpec: DataSpec): String =
        buildString {
            append("echo-remote-v2:")
            append(EchoRemotePlaybackAuthRegistry.cacheIdentity(dataSpec.uri, dataSpec.httpRequestHeaders))
            append(':')
            append(dataSpec.key ?: dataSpec.uri.toRemotePlaybackCacheKey())
        }
}

fun webDavPlaybackUriRequiresCredential(uri: String): Boolean {
    val trimmed = uri.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return false
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return false
    val afterScheme = trimmed.substring(schemeEnd + 3)
    val authority = afterScheme.substringBefore('/')
    if ('@' in authority) return false
    val path = "/${afterScheme.substringAfter('/', missingDelimiterValue = "")}".lowercase()
    if (path == "/rest" || path.startsWith("/rest/") || path.contains("/rest/")) return false
    return true
}

fun webDavCredentialCoversUri(uri: String, normalizedBaseUrl: String): Boolean {
    val clean = stripHttpUserInfo(uri.trim())
    val base = normalizedBaseUrl.trim().trimEnd('/')
    if (base.isBlank() || clean.isBlank()) return false
    return clean == base || clean.startsWith("$base/")
}

fun webDavAuthReadyForQueue(
    uris: Iterable<String>,
    credentialBaseUrls: Iterable<String>,
): Boolean {
    val needing = uris.filter(::webDavPlaybackUriRequiresCredential)
    if (needing.isEmpty()) return true
    val bases = credentialBaseUrls.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }
    if (bases.isEmpty()) return false
    return needing.all { uri -> bases.any { webDavCredentialCoversUri(uri, it) } }
}

fun queueRequiresWebDavAuth(uris: Iterable<String>): Boolean =
    uris.any(::webDavPlaybackUriRequiresCredential)

fun subsonicPlaybackUriRequiresCredential(uri: String): Boolean {
    val trimmed = uri.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return false
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return false
    val afterScheme = trimmed.substring(schemeEnd + 3)
    val path = "/${afterScheme.substringAfter('/', missingDelimiterValue = "")}".lowercase()
    return path == "/rest" || path.startsWith("/rest/") || path.contains("/rest/")
}

fun queueRequiresSubsonicAuth(uris: Iterable<String>): Boolean =
    uris.any(::subsonicPlaybackUriRequiresCredential)

fun subsonicAuthReadyForQueue(
    uris: Iterable<String>,
    credentialBaseUrls: Iterable<String>,
): Boolean {
    val needing = uris.filter(::subsonicPlaybackUriRequiresCredential)
    if (needing.isEmpty()) return true
    val bases = credentialBaseUrls.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }
    if (bases.isEmpty()) return false
    return needing.all { uri -> bases.any { webDavCredentialCoversUri(uri, it) } }
}

private fun stripHttpUserInfo(uri: String): String {
    val schemeEnd = uri.indexOf("://")
    if (schemeEnd <= 0) return uri
    val afterScheme = uri.substring(schemeEnd + 3)
    val at = afterScheme.indexOf('@')
    if (at <= 0) return uri
    val slash = afterScheme.indexOf('/')
    if (slash in 1 until at) return uri
    return uri.substring(0, schemeEnd + 3) + afterScheme.substring(at + 1)
}

internal fun applySubsonicTokenAuth(
    url: String,
    credential: EchoSubsonicPlaybackCredential,
    salt: String = randomSubsonicTokenSalt(),
): String {
    val token = md5Hex(credential.password + salt)
    val kept = parseQueryParameters(url)
        .filterNot { it.first.lowercase() in subsonicAuthQueryNames }
    val auth = listOf(
        "u" to credential.username.trim(),
        "t" to token,
        "s" to salt,
        "v" to SubsonicPlaybackApiVersion,
        "c" to SubsonicPlaybackClientId,
    )
    return replaceQuery(url, kept + auth)
}

private fun isSubsonicRestUrl(url: String): Boolean {
    val path = runCatching { URI(url).path }.getOrNull() ?: return false
    val lower = path.lowercase()
    return lower.contains("/rest/") || lower.endsWith("/rest")
}

private fun parseQueryParameters(url: String): List<Pair<String, String>> {
    val rawQuery = runCatching { URI(url).rawQuery }.getOrNull() ?: return emptyList()
    if (rawQuery.isBlank()) return emptyList()
    return rawQuery.split('&').mapNotNull { part ->
        if (part.isEmpty()) return@mapNotNull null
        val separator = part.indexOf('=')
        if (separator < 0) {
            part.urlDecode() to ""
        } else {
            part.substring(0, separator).urlDecode() to part.substring(separator + 1).urlDecode()
        }
    }
}

private fun replaceQuery(url: String, query: List<Pair<String, String>>): String {
    val uri = URI(url)
    val base = buildString {
        append(uri.scheme)
        append("://")
        append(uri.rawAuthority ?: uri.authority)
        append(uri.rawPath ?: "")
    }
    if (query.isEmpty()) return base
    return query.joinToString("&", prefix = "$base?") { (name, value) ->
        "${name.urlEncode()}=${value.urlEncode()}"
    }
}

private fun stripUserInfo(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    if (uri.userInfo.isNullOrBlank()) return url
    return URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
}

private fun userInfoFromUrl(url: String): String? =
    runCatching { URI(url).userInfo }.getOrNull()

private fun hostFromUrl(url: String): String? =
    runCatching { URI(url).host }.getOrNull()

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.urlDecode(): String =
    URLDecoder.decode(this, StandardCharsets.UTF_8.name())

private fun randomSubsonicTokenSalt(): String =
    ByteArray(12)
        .also(SubsonicTokenSaltRandom::nextBytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun md5Hex(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun basicAuthorization(username: String, password: String): String {
    val raw = "$username:$password"
    val encoded = Base64.encodeToString(raw.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    return "Basic $encoded"
}

private fun basicAuthorizationFromUserInfo(userInfo: String): String? {
    val separator = userInfo.indexOf(':')
    if (separator <= 0) return null
    return basicAuthorization(
        username = userInfo.substring(0, separator),
        password = userInfo.substring(separator + 1),
    )
}

private fun Uri.userInfoDecoded(): String? =
    encodedUserInfo?.let { encoded ->
        runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }.getOrNull()
    }

private fun Uri.withoutUserInfo(): Uri {
    if (encodedUserInfo.isNullOrBlank()) return this
    return buildUpon()
        .encodedAuthority(
            buildString {
                append(host.orEmpty())
                if (port != -1) append(':').append(port)
            },
        )
        .build()
}

private fun Uri.isRemotePlaybackUri(): Boolean =
    scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)

private fun Uri.toRemotePlaybackCacheKey(): String {
    val cleanUri = withoutUserInfo()
    if (!cleanUri.isHierarchical || cleanUri.encodedQuery.isNullOrBlank()) {
        return cleanUri.toString()
    }

    val builder = cleanUri.buildUpon().clearQuery()
    cleanUri.queryParameterNames
        .filterNot { it.isSensitiveCacheQueryName() }
        .sorted()
        .forEach { name ->
            cleanUri.getQueryParameters(name).forEach { value ->
                builder.appendQueryParameter(name, value)
            }
        }
    return builder.build().toString()
}

private fun String.isSensitiveCacheQueryName(): Boolean =
    lowercase() in sensitiveCacheQueryNames

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }

internal fun remotePlaybackCacheNamespace(
    credentialIdentity: String? = null,
    userInfoIdentity: String? = null,
    authorizationHeaders: List<String> = emptyList(),
    sensitiveQueryValues: List<Pair<String, String>> = emptyList(),
): String {
    val identityParts = buildList {
        credentialIdentity?.let { add("webdav:$it") }
            ?: userInfoIdentity?.let { add("userinfo:$it") }
        authorizationHeaders.sorted().forEach { add("authorization:$it") }
        sensitiveQueryValues
            .sortedWith(compareBy<Pair<String, String>> { it.first.lowercase() }.thenBy { it.second })
            .forEach { (name, value) -> add("query:${name.lowercase()}:$value") }
    }
    return sha256(identityParts.ifEmpty { listOf("public") }.joinToString("\u0000"))
}

private val SubsonicTokenSaltRandom = SecureRandom()

private const val SubsonicPlaybackApiVersion = "1.16.1"
private const val SubsonicPlaybackClientId = "ECHOAndroid"

private val subsonicAuthQueryNames = setOf("u", "t", "s", "p", "v", "c", "f")

private val sensitiveCacheQueryNames = setOf(
    "access_token",
    "api_key",
    "apikey",
    "auth",
    "authorization",
    "key",
    "p",
    "pass",
    "passwd",
    "password",
    "pwd",
    "s",
    "salt",
    "t",
    "token",
    "u",
    "user",
    "username",
)
