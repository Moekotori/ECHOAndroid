package app.echo.android.data

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.jsonObjects(name: String): List<JSONObject> {
    val array = optJSONArray(name)
    if (array != null) {
        return array.objectList()
    }
    val single = optJSONObject(name)
    if (single != null) {
        return listOf(single)
    }
    return emptyList()
}

fun JSONArray.objectList(): List<JSONObject> {
    val items = ArrayList<JSONObject>(length())
    for (index in 0 until length()) {
        optJSONObject(index)?.let(items::add)
    }
    return items
}
