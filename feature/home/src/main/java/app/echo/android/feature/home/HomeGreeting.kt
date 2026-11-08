package app.echo.android.feature.home

internal data class HomeGreeting(val title: Int, val subtitle: Int)

/** Maps the local hour (0-23) to a greeting; no I/O, so it is safe to call during composition. */
internal fun homeGreetingForHour(hour: Int): HomeGreeting = when (hour) {
    in 5..10 -> HomeGreeting(R.string.home_greeting_morning, R.string.home_greeting_morning_sub)
    in 11..13 -> HomeGreeting(R.string.home_greeting_noon, R.string.home_greeting_noon_sub)
    in 14..17 -> HomeGreeting(R.string.home_greeting_afternoon, R.string.home_greeting_ask_sub)
    in 18..22 -> HomeGreeting(R.string.home_greeting_evening, R.string.home_greeting_ask_sub)
    else -> HomeGreeting(R.string.home_greeting_night, R.string.home_greeting_night_sub)
}
