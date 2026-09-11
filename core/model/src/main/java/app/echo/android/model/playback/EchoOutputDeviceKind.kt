package app.echo.android.model.playback

enum class EchoOutputDeviceKind(val id: String) {
    System("system"),
    Speaker("speaker"),
    Wired("wired"),
    Bluetooth("bluetooth"),
    Usb("usb"),
    Other("other"),
    ;

    companion object {
        fun fromId(value: String?): EchoOutputDeviceKind =
            entries.firstOrNull { it.id == value } ?: System
    }
}
