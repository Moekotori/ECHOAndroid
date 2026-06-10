package app.echo.android.design

import java.util.concurrent.atomic.AtomicReference

object EchoArtworkRequestHeadersRegistry {
    private val rules = AtomicReference<List<EchoArtworkHeaderRule>>(emptyList())

    fun replaceEchoLinkAuthorization(
        baseUrl: String?,
        token: String?,
    ) {
        val safeBaseUrl = baseUrl
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
        val safeToken = token
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        rules.set(
            if (safeBaseUrl == null || safeToken == null) {
                emptyList()
            } else {
                listOf(
                    EchoArtworkHeaderRule(
                        urlPrefix = "$safeBaseUrl/",
                        headers = mapOf(
                            "Authorization" to "Bearer $safeToken",
                            "X-ECHO-Link-Version" to "1",
                        ),
                    ),
                )
            },
        )
    }

    fun headersFor(url: String?): Map<String, String> {
        val rawUrl = url?.trim()?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return rules.get()
            .firstOrNull { rawUrl.startsWith(it.urlPrefix) }
            ?.headers
            .orEmpty()
    }
}

private data class EchoArtworkHeaderRule(
    val urlPrefix: String,
    val headers: Map<String, String>,
)
