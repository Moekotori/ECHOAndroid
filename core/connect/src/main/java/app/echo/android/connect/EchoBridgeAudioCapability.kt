package app.echo.android.connect

import java.util.Locale
import org.json.JSONObject

/** New bridges distinguish receiver formats; older bridges still expose the source codec. */
internal fun JSONObject.bridgeCanPlayOnPhone(): Boolean {
    val codec = (optText("codec") ?: optJSONObject("audio")?.optText("codec"))?.lowercase(Locale.ROOT)
    val unsupported = codec == "tta" || codec == "true audio" || codec == "dsf" || codec == "dff" || codec?.contains("dsd") == true
    return optBoolean("canPlay", false) && optBoolean("canPlayOnPhone", true) && !unsupported
}
