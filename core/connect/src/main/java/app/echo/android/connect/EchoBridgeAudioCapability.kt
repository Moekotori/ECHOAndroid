package app.echo.android.connect

import java.util.Locale
import org.json.JSONObject

/** New bridges distinguish receiver formats; older bridges still expose the source codec. */
internal fun JSONObject.bridgeCanPlayOnPhone(): Boolean {
    if (!optBoolean("canPlay", false)) return false
    val audio = optJSONObject("audio")
    val codec = (optText("codec") ?: audio?.optText("codec"))?.lowercase(Locale.ROOT)
    if (codec == "tta" || codec == "true audio") return false
    if (optBoolean("canPlayOnPhone", true)) return true

    // Bridge v1 can serve these original files but labels them unsupported for older phone players.
    val mimeType = audio?.optText("mimeType")?.lowercase(Locale.ROOT)
    return when (mimeType) {
        "audio/x-dsf", "audio/x-dff" -> true
        null -> codec == "dsf" || codec == "dff" || codec == "dst" || codec?.startsWith("dsd") == true
        else -> false
    }
}
