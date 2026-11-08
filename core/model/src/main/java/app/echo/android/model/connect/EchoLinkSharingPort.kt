package app.echo.android.model.connect

object EchoLinkSharingPort {
    const val DefaultPhone = 26801

    fun isValid(port: Int): Boolean = port in 1..65535

    fun requireValid(port: Int): Int {
        require(isValid(port)) { "invalid_phone_library_port" }
        return port
    }
}
