package app.echo.android.feature.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.rememberEchoWindowLayout
import kotlinx.coroutines.launch

/** The header moves the sheet; touches in the editor belong to its faders and scrolling body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val window = rememberEchoWindowLayout()
    var closing by remember { mutableStateOf(false) }
    var editorTouched by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch { sheet.hide(); onDismiss() }
    }
    EchoModalSheet(
        modifier = modifier,
        sheetState = sheet,
        sheetGesturesEnabled = !editorTouched && !closing,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = (window.heightDp * 0.82f).dp).imePadding()) {
            EchoSheetHeader(
                title = stringResource(R.string.feature_settings_equalizer_7ccb03),
                trailing = {
                    IconButton(onClick = ::close, enabled = !closing) {
                        EchoIcon(Icons.Rounded.Close, stringResource(R.string.equalizer_sheet_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            editorTouched = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                            } finally { editorTouched = false }
                        }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) { content() }
        }
    }
}
