package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoOverlayBackHandler
import app.echo.android.design.EchoPageBack
import app.echo.android.design.EchoPageContent
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPageBackgroundColor
import app.echo.android.design.rememberEchoBackProgress

/** Sound editors own a page; the index retains its scroll position when returning. */
@Composable
internal fun SignalSoundNavigation(
    selectedPanel: Int,
    labels: List<String>,
    isActive: Boolean,
    onBack: () -> Unit,
    tool: @Composable (Int) -> Unit,
    scrollingTool: Int? = null,
    onNavigateUp: (() -> Unit)? = null,
    home: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close = {
        focusManager.clearFocus()
        keyboard?.hide()
        (onNavigateUp ?: onBack)()
    }
    val stateHolder = rememberSaveableStateHolder()
    val back = rememberEchoBackProgress(LocalEchoEffectivePerformanceMode.current.isLightweight)
    EchoOverlayBackHandler(
        enabled = isActive && selectedPanel >= 0,
        onProgress = { if (onNavigateUp == null) back.update(it) },
        onCancel = { back.restore() },
    ) {
        back.reset()
        close()
    }
    EchoPageContent(
        targetState = selectedPanel,
        contentKey = { if (it < 0) "sound-home" else "sound-tool-$it" },
        isBackward = { _, target -> target < 0 },
        modifier = Modifier.fillMaxSize(),
        label = "signal-sound-navigation",
        back = if (selectedPanel >= 0) EchoPageBack(-1) { back.value } else null,
    ) { panel ->
        stateHolder.SaveableStateProvider(panel) {
            if (panel < 0) {
                home()
            } else {
                Column(
                    Modifier.fillMaxSize().background(echoPageBackgroundColor()).statusBarsPadding().imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxSize()) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SignalSoundToolHeader(labels[panel], onBack = {
                                back.reset()
                                close()
                            })
                        }
                        if (panel == scrollingTool) {
                            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp)) {
                                tool(panel)
                            }
                        } else Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 188.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            tool(panel)
                        }
                    }
                }
            }
        }
    }
}
