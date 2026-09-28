package app.echo.android.model.listening

enum class EchoListeningConnection {
    Offline,
    Connecting,
    Online,
    Reconnecting,
}

enum class EchoListeningProgramme {
    Playing,
    Paused,
    Stopped,
}

enum class EchoListeningAudio {
    Off,
    Buffering,
    Receiving,
    Paused,
    Interrupted,
    Error,
}

/** Stable server error codes. Never carry the raw close reason or a credential. */
enum class EchoListeningError {
    ServerUnreachable,
    ProtocolMismatch,
    ServerPasswordRequired,
    ServerFull,
    ServerTooOld,
    InvalidInput,
    RoomNotFound,
    WrongPassword,
    RoomFull,
    AlreadyInRoom,
    SessionChanged,
    OutputBusy,
    PlaybackFailed,
    ConnectionClosed,
    HeartbeatTimeout,
    SlowReceiver,
    RequestTimeout,
    ChatRateLimit,
    InvalidChat,
    NotConnected,
    Generic,
}

data class EchoListeningInvite(
    val server: String,
    val roomId: String? = null,
    val invitation: String? = null,
)

data class EchoListeningLimits(
    val maxUsers: Int = 0,
    val maxRooms: Int = 0,
    val maxRoomUsers: Int = 0,
)

data class EchoListeningCapabilities(
    val fixedAudioBitrate: Int? = null,
    val chat: Boolean = false,
    val trackMetadata: Boolean = false,
    val programmeState: Boolean = false,
) {
    val acceptsGuestAudio: Boolean get() = fixedAudioBitrate == FIXED_GUEST_BITRATE

    companion object {
        const val FIXED_GUEST_BITRATE = 256_000
    }
}

data class EchoListeningMember(
    val id: String,
    val name: String,
    val online: Boolean,
    val self: Boolean = false,
)

data class EchoListeningLyricLine(
    val timeMs: Long,
    val text: String,
)

data class EchoListeningTrack(
    val title: String,
    val artist: String,
    val album: String,
    val coverWebp: ByteArray?,
    val lines: List<EchoListeningLyricLine>,
)

data class EchoListeningRoomSummary(
    val id: String,
    val name: String,
    val locked: Boolean,
    val memberCount: Int,
    val maxUsers: Int,
)

data class EchoListeningRoom(
    val id: String,
    val name: String,
    val locked: Boolean,
    val memberCount: Int,
    val maxUsers: Int,
    val hostId: String?,
    val streamEpoch: Long,
    val programme: EchoListeningProgramme,
    val title: String,
    val track: EchoListeningTrack?,
    val members: List<EchoListeningMember>,
) {
    val playing: Boolean
        get() = programme == EchoListeningProgramme.Playing && streamEpoch > 0L
}

data class EchoListeningChatMessage(
    val id: String,
    val roomId: String,
    val senderId: String,
    val name: String,
    val text: String,
    val sentAtEpochMs: Long,
    val self: Boolean,
)

data class EchoListeningState(
    val connection: EchoListeningConnection = EchoListeningConnection.Offline,
    val server: String = "",
    val serverName: String = "",
    val peerId: String = "",
    val rooms: List<EchoListeningRoomSummary> = emptyList(),
    val room: EchoListeningRoom? = null,
    val chat: List<EchoListeningChatMessage> = emptyList(),
    val audio: EchoListeningAudio = EchoListeningAudio.Off,
    val volume: Float = 1f,
    val error: EchoListeningError? = null,
    val yieldedToLocal: Boolean = false,
    val chatEnabled: Boolean = false,
    val passwordRoomId: String? = null,
) {
    val inRoom: Boolean get() = room != null
    val busy: Boolean
        get() = connection == EchoListeningConnection.Connecting ||
            connection == EchoListeningConnection.Reconnecting
}
