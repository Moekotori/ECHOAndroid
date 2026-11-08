package app.echo.android.data.update

import app.echo.android.model.update.GithubUpdateSource

internal const val UPDATE_METADATA_URL =
    "https://github.com/moekotori/echoandroid/releases/latest/download/update.json"

/** Only wrap validated release assets; manifests must still contain the canonical GitHub URL. */
internal fun GithubUpdateSource.resolveUpdateUrl(url: String): String {
    require(url == UPDATE_METADATA_URL || isReleaseAssetUrl(url))
    val prefix = when (this) {
        GithubUpdateSource.Direct -> ""
        GithubUpdateSource.GhFast -> "https://ghfast.top/"
        GithubUpdateSource.GhProxyNet -> "https://ghproxy.net/"
        GithubUpdateSource.GhProxy -> "https://gh-proxy.org/"
        GithubUpdateSource.GhProxyHk -> "https://hk.gh-proxy.org/"
        GithubUpdateSource.GhProxyCdn -> "https://cdn.gh-proxy.org/"
        GithubUpdateSource.GhProxyEdgeOne -> "https://edgeone.gh-proxy.org/"
    }
    return prefix + url
}
