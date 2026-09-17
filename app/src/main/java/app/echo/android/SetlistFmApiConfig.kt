package app.echo.android

internal object SetlistFmApiConfig {
    val API_KEY: String
        get() = BuildConfig.SETLISTFM_API_KEY.trim()

    val HAS_API_KEY: Boolean
        get() = API_KEY.isNotBlank()
}
