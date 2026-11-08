package app.echo.android.model.update

/** Stable preference IDs shared by the update transport and settings UI. */
enum class GithubUpdateSource(val id: String) {
    Direct("github"),
    GhFast("ghfast"),
    GhProxyNet("ghproxy-net"),
    GhProxy("gh-proxy"),
    GhProxyHk("gh-proxy-hk"),
    GhProxyCdn("gh-proxy-cdn"),
    GhProxyEdgeOne("gh-proxy-edgeone"),
    ;

    companion object {
        fun fromId(id: String?): GithubUpdateSource = entries.firstOrNull { it.id == id } ?: Direct
    }
}
