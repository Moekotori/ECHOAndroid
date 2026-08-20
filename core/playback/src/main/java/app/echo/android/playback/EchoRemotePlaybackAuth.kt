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
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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

object EchoRemotePlaybackAuthRegistry {
    private val webDavCredentials = AtomicReference<List<EchoWebDavPlaybackCredential>>(emptyList())

    fun replaceWebDavCredentials(credentials: List<EchoWebDavPlaybackCredential>) {
        webDavCredentials.set(
            credentials
                .filter { it.normalizedBaseUrl.isNotBlank() && it.username.isNotBlank() && it.password.isNotBlank() }
                .distinctBy { it.normalizedBaseUrl },
        )
    }

    @UnstableApi
    internal fun resolve(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        val userInfo = uri.userInfoDecoded()
        val matchedCredential = matchingCredential(uri)
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

    internal fun cacheIdentity(uri: Uri, requestHeaders: Map<String, String>): String {
        val credentialIdentity = matchingCredential(uri)?.let { credential ->
            "${credential.normalizedBaseUrl}:${credential.username.trim()}"
        }
        val userInfoIdentity = if (credentialIdentity == null) {
            uri.userInfoDecoded()?.takeIf { it.isNotBlank() }?.let { "${uri.host.orEmpty()}:$it" }
        } else {
            null
        }
        val authorizationHeaders = requestHeaders.entries
            .filter { it.key.equals("Authorization", ignoreCase = true) }
            .map { it.value }
        val sensitiveQueryValues = if (uri.isHierarchical && !uri.encodedQuery.isNullOrBlank()) {
            uri.queryParameterNames
                .filter { it.isSensitiveCacheQueryName() }
                .sorted()
                .flatMap { name -> uri.getQueryParameters(name).sorted().map { value -> name to value } }
        } else {
            emptyList()
        }
        return remotePlaybackCacheNamespace(
            credentialIdentity = credentialIdentity,
            userInfoIdentity = userInfoIdentity,
            authorizationHeaders = authorizationHeaders,
            sensitiveQueryValues = sensitiveQueryValues,
        )
    }

    private fun matchingCredential(uri: Uri): EchoWebDavPlaybackCredential? {
        val cleanUriString = uri.withoutUserInfo().toString()
        return webDavCredentials.get()
            .firstOrNull { credential ->
                cleanUriString == credential.normalizedBaseUrl ||
                    cleanUriString.startsWith("${credential.normalizedBaseUrl}/")
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
