package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteSourceRange
import org.json.JSONObject

internal fun JSONObject.sourceRange(): EchoRemoteSourceRange? = if (!has("sourceRange")) null else {
    val range = getJSONObject("sourceRange")
    EchoRemoteSourceRange(range.getDouble("startSeconds"),
        if (range.isNull("endSeconds")) null else range.getDouble("endSeconds"))
}

internal fun EchoRemoteSourceRange.toJson(): JSONObject = JSONObject().put("startSeconds", startSeconds)
    .put("endSeconds", endSeconds ?: JSONObject.NULL)
