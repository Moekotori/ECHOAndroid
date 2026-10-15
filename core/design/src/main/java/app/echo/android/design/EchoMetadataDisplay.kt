package app.echo.android.design

import java.util.Locale

private val UnknownMetadataSentinels = setOf(
    "未知艺术家",
    "未知专辑",
    "未知曲目",
    "<unknown>",
    "unknown artist",
    "unknown album",
    "unknown track",
    "不明なアーティスト",
    "不明なアルバム",
    "不明な曲",
)

fun displayMetadataOrUnknown(value: String?, unknown: String): String {
    val trimmed = value?.trim().orEmpty()
    return if (trimmed.isEmpty() || trimmed.lowercase(Locale.ROOT) in UnknownMetadataSentinels) unknown else trimmed
}
