package app.echo.android.listening

import app.echo.android.model.listening.EchoListeningCapabilities
import app.echo.android.model.listening.EchoListeningChatMessage
import app.echo.android.model.listening.EchoListeningError
import app.echo.android.model.listening.EchoListeningLimits
import app.echo.android.model.listening.EchoListeningLyricLine
import app.echo.android.model.listening.EchoListeningMember
import app.echo.android.model.listening.EchoListeningProgramme
import app.echo.android.model.listening.EchoListeningRoom
import app.echo.android.model.listening.EchoListeningRoomSummary
import app.echo.android.model.listening.EchoListeningTrack
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal object EchoListeningJson {
    fun errorCode(raw: String?): EchoListeningError = when (raw) {
        "protocol_mismatch" -> EchoListeningError.ProtocolMismatch
        "server_password_required" -> EchoListeningError.ServerPasswordRequired
        "server_full" -> EchoListeningError.ServerFull
        "invalid_input" -> EchoListeningError.InvalidInput
        "room_not_found" -> EchoListeningError.RoomNotFound
        "wrong_password" -> EchoListeningError.WrongPassword
        "room_full" -> EchoListeningError.RoomFull
        "already_in_room" -> EchoListeningError.AlreadyInRoom
        "session_changed" -> EchoListeningError.SessionChanged
        "server_unreachable" -> EchoListeningError.ServerUnreachable
        "connection_closed" -> EchoListeningError.ConnectionClosed
        "heartbeat_timeout" -> EchoListeningError.HeartbeatTimeout
        "slow_receiver", "slow_sender", "audio_rate_limit", "control_rate_limit" ->
            EchoListeningError.SlowReceiver
        "request_timeout" -> EchoListeningError.RequestTimeout
        "chat_rate_limit" -> EchoListeningError.ChatRateLimit
        "invalid_chat" -> EchoListeningError.InvalidChat
        "not_connected" -> EchoListeningError.NotConnected
        "hello_timeout" -> EchoListeningError.ConnectionClosed
        else -> EchoListeningError.Generic
    }

    fun closeReason(raw: String?): EchoListeningError {
        val code = raw?.trim()?.lowercase().orEmpty()
        return if (code.isEmpty()) EchoListeningError.ConnectionClosed else errorCode(code)
    }

    fun capabilities(result: JSONObject): EchoListeningCapabilities {
        val caps = result.optJSONObject("capabilities") ?: return EchoListeningCapabilities()
        return EchoListeningCapabilities(
            fixedAudioBitrate = caps.optInt("fixedAudioBitrate", 0).takeIf { it > 0 },
            chat = caps.optBoolean("chat"),
            trackMetadata = caps.optBoolean("trackMetadata"),
            programmeState = caps.optBoolean("programmeState"),
        )
    }

    fun limits(result: JSONObject): EchoListeningLimits {
        val limits = result.optJSONObject("limits") ?: return EchoListeningLimits()
        return EchoListeningLimits(
            maxUsers = limits.optInt("maxUsers"),
            maxRooms = limits.optInt("maxRooms"),
            maxRoomUsers = limits.optInt("maxRoomUsers"),
        )
    }

    fun roomSummaries(result: Any?): List<EchoListeningRoomSummary> {
        val array = result as? JSONArray ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = short(item, "id", 64) ?: continue
                add(
                    EchoListeningRoomSummary(
                        id = id,
                        name = short(item, "name", 80) ?: id,
                        locked = item.optBoolean("locked"),
                        memberCount = item.optInt("count").coerceAtLeast(0),
                        maxUsers = item.optInt("maxUsers").coerceAtLeast(0),
                    ),
                )
            }
        }
    }

    fun room(value: Any?, selfId: String): EchoListeningRoom? {
        val json = value as? JSONObject ?: return null
        val id = short(json, "id", 64) ?: return null
        val epoch = json.optLong("streamEpoch", 0L).coerceAtLeast(0L)
        val programme = programme(json.optString("programmeState"), epoch)
        val members = members(json.optJSONArray("members"), selfId)
        return EchoListeningRoom(
            id = id,
            name = short(json, "name", 80) ?: id,
            locked = json.optBoolean("locked"),
            memberCount = json.optInt("count", members.size).coerceAtLeast(0),
            maxUsers = json.optInt("maxUsers").coerceAtLeast(0),
            hostId = short(json, "hostId", 80),
            streamEpoch = epoch,
            programme = programme,
            title = short(json, "title", 160).orEmpty(),
            track = track(json.opt("track")),
            members = members,
        )
    }

    fun chat(value: Any?, selfId: String): EchoListeningChatMessage? {
        val json = value as? JSONObject ?: return null
        val id = short(json, "id", 80) ?: return null
        val roomId = short(json, "roomId", 64) ?: return null
        val text = json.optString("text").trim()
        if (text.isEmpty() || text.length > 500) return null
        val senderId = short(json, "senderId", 80).orEmpty()
        return EchoListeningChatMessage(
            id = id,
            roomId = roomId,
            senderId = senderId,
            name = short(json, "name", 48) ?: senderId,
            text = text,
            sentAtEpochMs = json.optLong("sentAt").coerceAtLeast(0L),
            self = senderId.isNotEmpty() && senderId == selfId,
        )
    }

    private fun members(array: JSONArray?, selfId: String): List<EchoListeningMember> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length().coerceAtMost(32)) {
                val item = array.optJSONObject(index) ?: continue
                val id = short(item, "id", 80) ?: continue
                add(
                    EchoListeningMember(
                        id = id,
                        name = short(item, "name", 48) ?: id,
                        online = item.optBoolean("online"),
                        self = id == selfId,
                    ),
                )
            }
        }
    }

    private fun track(value: Any?): EchoListeningTrack? {
        val json = value as? JSONObject ?: return null
        val lines = lyrics(json.optJSONObject("lyrics"))
        return EchoListeningTrack(
            title = short(json, "title", 160).orEmpty(),
            artist = short(json, "artist", 160).orEmpty(),
            album = short(json, "album", 160).orEmpty(),
            coverWebp = cover(json.optString("cover")),
            lines = lines,
        )
    }

    private fun lyrics(json: JSONObject?): List<EchoListeningLyricLine> {
        val array = json?.optJSONArray("lines") ?: return emptyList()
        val lines = ArrayList<EchoListeningLyricLine>(minOf(array.length(), 400))
        var chars = 0
        for (index in 0 until array.length().coerceAtMost(400)) {
            val item = array.optJSONObject(index) ?: continue
            val text = short(item, "text", 1000) ?: continue
            if (chars + text.length > 8_000) break
            chars += text.length
            lines += EchoListeningLyricLine(
                timeMs = item.optLong("timeMs").coerceIn(0L, 86_400_000L),
                text = text,
            )
        }
        return lines
    }

    private fun cover(encoded: String): ByteArray? {
        if (encoded.isEmpty() || encoded.length > 16_384) return null
        if (!encoded.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) return null
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size < 12 || bytes.size > 4096) return null
        if (!bytes.startsWithAscii(0, "RIFF") || !bytes.startsWithAscii(8, "WEBP")) return null
        return bytes
    }

    private fun programme(raw: String, epoch: Long): EchoListeningProgramme = when (raw) {
        "playing" -> EchoListeningProgramme.Playing
        "paused" -> EchoListeningProgramme.Paused
        "stopped" -> EchoListeningProgramme.Stopped
        else -> if (epoch > 0L) EchoListeningProgramme.Playing else EchoListeningProgramme.Stopped
    }

    private fun short(json: JSONObject, name: String, max: Int): String? {
        if (!json.has(name) || json.isNull(name)) return null
        val value = json.optString(name).trim()
        if (value.isEmpty()) return null
        return value.take(max)
    }

    private fun ByteArray.startsWithAscii(offset: Int, ascii: String): Boolean {
        if (size < offset + ascii.length) return false
        for (index in ascii.indices) {
            if (this[offset + index] != ascii[index].code.toByte()) return false
        }
        return true
    }
}
