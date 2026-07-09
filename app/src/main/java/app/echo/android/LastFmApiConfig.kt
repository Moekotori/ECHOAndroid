package app.echo.android

internal object LastFmApiConfig {
    val API_KEY: String
        get() = BuildConfig.LASTFM_API_KEY.trim()

    val SHARED_SECRET: String
        get() = BuildConfig.LASTFM_SHARED_SECRET.trim()

    val HAS_API_KEY: Boolean
        get() = API_KEY.isNotBlank()

    val HAS_SHARED_SECRET: Boolean
        get() = SHARED_SECRET.isNotBlank()
}
