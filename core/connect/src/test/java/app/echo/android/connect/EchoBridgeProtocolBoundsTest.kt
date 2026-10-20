package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EchoBridgeProtocolBoundsTest {
    private val endpoint = EchoRemoteEndpoint("bridge", "Bridge", "127.0.0.1", 49234, "", libraryOnly = true)

    @Test fun separatesPhoneSupportFromComputerPlaybackIncludingOlderBridges() = runBlocking {
        val rows = JSONArray().put(JSONObject().put("id","tta").put("title","TTA").put("codec","tta").put("canPlay",true))
            .put(JSONObject().put("id","ape").put("title","APE").put("codec","ape").put("canPlay",true))
            .put(JSONObject().put("id","disabled").put("title","Disabled").put("codec","flac").put("canPlay",true).put("canPlayOnPhone",false))
        val transport = EchoBridgeTransport { JSONObject().put("catalog",JSONObject().put("entries",rows).put("total",3).put("page",1).put("pageSize",50)) }
        assertEquals(listOf(false,true,false),transport.tracks(endpoint,"",1).tracks.map { it.canPlayOnPhone })
        val selected = EchoBridgeTransport { JSONObject().put("track",rows.getJSONObject(0).put("streamUrl","http://127.0.0.1:49234/echo-link/media/test")) }
        val error = runCatching { selected.stream(endpoint,"tta") }.exceptionOrNull() as EchoLinkHttpException
        assertEquals(415,error.statusCode)
    }

    @Test fun acceptsTheShared4000LineLimitAndRejectsOverflow() = runBlocking {
        val lines=JSONArray(); repeat(4000) { lines.put(JSONObject().put("timeMs",it).put("text","Line")) }
        val source=JSONObject().put("kind","synced").put("offsetMs",-120).put("lines",lines)
        val transport=EchoBridgeTransport { JSONObject().put("lyrics",source) }
        val result=JSONObject(transport.lyrics(endpoint,"song")!!.rawText)
        assertEquals(4000,result.getJSONArray("lines").length()); assertEquals(-120,result.getInt("offsetMs"))
        lines.put(JSONObject().put("timeMs",4000).put("text","Overflow"))
        assertTrue(runCatching { transport.lyrics(endpoint,"song") }.exceptionOrNull() is IllegalArgumentException)
    }
}
