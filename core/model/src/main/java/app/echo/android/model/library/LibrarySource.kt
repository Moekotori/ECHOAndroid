@file:Suppress("SpellCheckingInspection") // id 是持久化在数据库与订阅协议中的稳定标识,不能改写拼写

package app.echo.android.model.library

data class LibrarySource(
    val id: String,
) {
    companion object {
        val MediaStore = LibrarySource("mediastore")
        val Subsonic = LibrarySource("subsonic")
        val WebDav = LibrarySource("webdav")
        val Unknown = LibrarySource("unknown")
    }
}
