package app.echo.android.feature.listening

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.listening.EchoListeningAudio
import app.echo.android.model.listening.EchoListeningConnection
import app.echo.android.model.listening.EchoListeningError
import app.echo.android.model.listening.EchoListeningMember
import app.echo.android.model.listening.EchoListeningProgramme
import app.echo.android.model.listening.EchoListeningRoomSummary
import app.echo.android.model.listening.EchoListeningState

@Composable
fun ListeningScreen(
    state: EchoListeningState,
    initialInput: String = "",
    defaultName: String = "",
    onBack: () -> Unit,
    onConnect: (input: String, name: String, serverPassword: String) -> Unit,
    onRefresh: () -> Unit,
    onJoin: (roomId: String, password: String?) -> Unit,
    onLeave: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: (String) -> Unit,
    onVolume: (Float) -> Unit,
    onResumeAudio: () -> Unit,
    onDismissPassword: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var passwordRoom by remember { mutableStateOf<EchoListeningRoomSummary?>(null) }
    var passwordText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.room?.id) {
        if (state.room != null) passwordRoom = null
    }
    Column(
        modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.listening_back))
            }
            Text(stringResource(R.string.listening_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.connection != EchoListeningConnection.Offline) {
                TextButton(onClick = onDisconnect, enabled = !state.busy) { Text(stringResource(R.string.listening_disconnect)) }
            }
        }
        state.error?.let { error ->
            if (error != EchoListeningError.WrongPassword) {
                Text(
                    error.label(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
        if (state.room != null) {
            ListeningRoom(
                state = state,
                onLeave = onLeave,
                onChat = onChat,
                onVolume = onVolume,
                onResumeAudio = onResumeAudio,
                modifier = Modifier.weight(1f),
            )
        } else {
            ListeningLobby(
                state = state,
                initialInput = initialInput,
                defaultName = defaultName,
                onConnect = onConnect,
                onRefresh = onRefresh,
                onJoin = { room ->
                    if (room.locked) {
                        passwordRoom = room
                        passwordText = ""
                    } else {
                        onJoin(room.id, null)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
    val passwordRoomId = passwordRoom?.id ?: state.passwordRoomId
    if (passwordRoomId != null) {
        AlertDialog(
            onDismissRequest = {
                passwordRoom = null
                onDismissPassword()
            },
            title = { Text(stringResource(R.string.listening_password)) },
            text = {
                Column {
                    if (state.error == EchoListeningError.WrongPassword) {
                        Text(stringResource(R.string.listening_wrong_password), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onJoin(passwordRoomId, passwordText)
                    },
                    enabled = passwordText.isNotEmpty() && !state.busy,
                ) { Text(stringResource(R.string.listening_join)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    passwordRoom = null
                    onDismissPassword()
                }) { Text(stringResource(R.string.listening_back)) }
            },
        )
    }
}

@Composable
private fun ListeningLobby(
    state: EchoListeningState,
    initialInput: String,
    defaultName: String,
    onConnect: (input: String, name: String, serverPassword: String) -> Unit,
    onRefresh: () -> Unit,
    onJoin: (EchoListeningRoomSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf(initialInput) }
    LaunchedEffect(initialInput) {
        if (initialInput.isNotBlank()) input = initialInput
    }
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var serverPassword by rememberSaveable { mutableStateOf("") }
    val online = state.connection == EchoListeningConnection.Online || state.connection == EchoListeningConnection.Reconnecting
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!online) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.listening_server)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                label = { Text(stringResource(R.string.listening_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.error == EchoListeningError.ServerPasswordRequired) {
                OutlinedTextField(
                    value = serverPassword,
                    onValueChange = { serverPassword = it },
                    label = { Text(stringResource(R.string.listening_server_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = { onConnect(input, name, serverPassword) },
                enabled = !state.busy && input.isNotBlank() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.connection == EchoListeningConnection.Connecting) {
                        stringResource(R.string.listening_connecting)
                    } else {
                        stringResource(R.string.listening_connect)
                    },
                )
            }
        } else {
            Text(
                if (state.connection == EchoListeningConnection.Reconnecting) {
                    stringResource(R.string.listening_reconnecting)
                } else {
                    state.serverName.ifBlank { state.server }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.listening_rooms), modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh, enabled = !state.busy) { Text(stringResource(R.string.listening_refresh)) }
            }
            if (state.rooms.isEmpty()) {
                Text(stringResource(R.string.listening_empty_rooms), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.rooms.forEach { room ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(room.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.listening_members_count, room.memberCount, room.maxUsers) +
                                if (room.locked) " · " + stringResource(R.string.listening_locked) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { onJoin(room) }, enabled = !state.busy) {
                        Text(stringResource(R.string.listening_join))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ListeningRoom(
    state: EchoListeningState,
    onLeave: () -> Unit,
    onChat: (String) -> Unit,
    onVolume: (Float) -> Unit,
    onResumeAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val room = state.room ?: return
    var draft by rememberSaveable { mutableStateOf("") }
    Column(modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(room.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onLeave) { Text(stringResource(R.string.listening_leave)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            val cover = remember(room.track?.coverWebp) { room.track?.coverWebp.toImageBitmap() }
            if (cover != null) {
                Image(cover, contentDescription = null, modifier = Modifier.size(72.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(room.track?.title?.ifBlank { room.title }.orEmpty().ifBlank { stringResource(R.string.listening_title) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val artist = room.track?.artist.orEmpty()
                if (artist.isNotEmpty()) {
                    Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(audioLabel(state, room.programme), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.yieldedToLocal) {
            Text(stringResource(R.string.listening_yielded), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onResumeAudio) { Text(stringResource(R.string.listening_resume)) }
        }
        Text(stringResource(R.string.listening_volume), style = MaterialTheme.typography.labelLarge)
        Slider(value = state.volume, onValueChange = onVolume)
        Text(stringResource(R.string.listening_members), style = MaterialTheme.typography.labelLarge)
        Text(membersLabel(room.members), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        if (room.track?.lines.isNullOrEmpty().not()) {
            Text(
                room.track?.lines?.take(8)?.joinToString("\n") { it.text }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.chat, key = { it.id }) { message ->
                Text(
                    stringResource(R.string.listening_chat_line, message.name, message.text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.chatEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.listening_chat_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (draft.isNotBlank()) {
                            onChat(draft)
                            draft = ""
                        }
                    }),
                    maxLines = 3,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onChat(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) { Text(stringResource(R.string.listening_send)) }
            }
        }
    }
}

@Composable
private fun audioLabel(state: EchoListeningState, programme: EchoListeningProgramme): String = when {
    state.yieldedToLocal -> stringResource(R.string.listening_yielded_short)
    state.audio == EchoListeningAudio.Buffering -> stringResource(R.string.listening_buffering)
    state.audio == EchoListeningAudio.Receiving -> stringResource(R.string.listening_receiving)
    state.audio == EchoListeningAudio.Interrupted -> stringResource(R.string.listening_interrupted)
    programme == EchoListeningProgramme.Paused -> stringResource(R.string.listening_paused)
    programme == EchoListeningProgramme.Stopped -> stringResource(R.string.listening_stopped)
    else -> stringResource(R.string.listening_paused)
}

@Composable
private fun membersLabel(members: List<EchoListeningMember>): String {
    if (members.isEmpty()) return stringResource(R.string.listening_empty_rooms)
    val parts = ArrayList<String>(members.size)
    for (member in members) {
        val name = if (member.self) stringResource(R.string.listening_you, member.name) else member.name
        parts += if (member.online) name else stringResource(R.string.listening_offline, name)
    }
    return parts.joinToString()
}

@Composable
private fun EchoListeningError.label(): String = stringResource(
    when (this) {
        EchoListeningError.ServerUnreachable -> R.string.listening_unreachable
        EchoListeningError.ProtocolMismatch, EchoListeningError.ServerTooOld -> R.string.listening_server_old
        EchoListeningError.ServerPasswordRequired -> R.string.listening_server_password
        EchoListeningError.ServerFull, EchoListeningError.RoomFull -> R.string.listening_room_full
        EchoListeningError.InvalidInput -> R.string.listening_invalid
        EchoListeningError.RoomNotFound -> R.string.listening_not_found
        EchoListeningError.WrongPassword -> R.string.listening_wrong_password
        EchoListeningError.AlreadyInRoom -> R.string.listening_already_in_room
        EchoListeningError.SessionChanged, EchoListeningError.ConnectionClosed, EchoListeningError.HeartbeatTimeout ->
            R.string.listening_closed
        EchoListeningError.OutputBusy -> R.string.listening_output_busy
        EchoListeningError.PlaybackFailed -> R.string.listening_playback_failed
        EchoListeningError.SlowReceiver -> R.string.listening_slow
        EchoListeningError.RequestTimeout -> R.string.listening_timeout
        EchoListeningError.ChatRateLimit -> R.string.listening_chat_limit
        EchoListeningError.InvalidChat -> R.string.listening_invalid
        EchoListeningError.NotConnected -> R.string.listening_closed
        EchoListeningError.Generic -> R.string.listening_generic
    },
)

private fun ByteArray?.toImageBitmap() = this?.let { bytes ->
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}
