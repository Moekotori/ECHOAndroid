package app.echo.android.connect

internal class EchoConnectionNoticeGate(private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    private val last = LinkedHashMap<String, Long>()
    fun clear() = last.clear()
    fun accept(key: String): Boolean {
        val time = now()
        if (last[key]?.let { time - it < 1_800 } == true) return false
        last[key] = time
        while (last.size > 16) last.remove(last.keys.first())
        return true
    }
}
