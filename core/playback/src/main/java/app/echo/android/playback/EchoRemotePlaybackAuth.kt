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
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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

    internal fun resolve(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        val uriString = uri.toString()
        val userInfo = uri.userInfoDecoded()
        val matchedCredential = webDavCredentials.get()
            .firstOrNull { credential ->
                uriString == credential.normalizedBaseUrl ||
                    uriString.startsWith("${credential.normalizedBaseUrl}/")
            }
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
    private const val MaxCacheBytes = 256L * 1024L * 1024L

    @Volatile
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache =
        cache ?: SimpleCache(
            File(context.cacheDir, "echo-remote-playback-cache"),
            LeastRecentlyUsedCacheEvictor(MaxCacheBytes),
        ).also { cache = it }
}

@UnstableApi
private object EchoRemotePlaybackCacheKeyFactory : CacheKeyFactory {
    override fun buildCacheKey(dataSpec: DataSpec): String =
        dataSpec.uri.toRemotePlaybackCacheKey()
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
