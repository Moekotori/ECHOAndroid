package app.echo.android.model.connect

enum class EchoRemotePlaybackOrder(val wireValue: String) {
    Sequential("sequential"),
    Shuffle("shuffle"),
    RepeatOne("repeat-one");

    companion object {
        fun fromWireValue(value: String?): EchoRemotePlaybackOrder? =
            entries.firstOrNull { it.wireValue == value }
    }
}
