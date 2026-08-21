package app.echo.android.design

private val UnknownMetadataSentinels = setOf("未知艺术家", "未知专辑", "未知曲目")

fun displayMetadataOrUnknown(value: String?, unknown: String): String {
    val trimmed = value?.trim().orEmpty()
    return if (trimmed.isEmpty() || trimmed in UnknownMetadataSentinels) unknown else trimmed
}
