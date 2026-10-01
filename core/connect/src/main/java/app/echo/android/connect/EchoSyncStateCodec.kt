package app.echo.android.connect

import app.echo.android.model.connect.EchoSyncState
import org.json.JSONObject

object EchoSyncStateCodec {
    fun encode(state: EchoSyncState): String = state.stateJson().toString()
    fun decode(text: String): EchoSyncState = JSONObject(text).syncState()
}
