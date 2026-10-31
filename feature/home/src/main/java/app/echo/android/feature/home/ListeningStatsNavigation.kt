package app.echo.android.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.echo.android.design.EchoOverlayBackHandler
import app.echo.android.design.EchoPageBack
import app.echo.android.design.EchoPageContent
import app.echo.android.design.rememberEchoBackProgress
import app.echo.android.model.playback.EchoMonthlyRecap
import app.echo.android.model.playback.ListeningStats
import app.echo.android.model.playback.ListeningStatsRange

/** Preserve the selected range and scroll position while visiting the monthly report. */
@Composable
fun ListeningStatsScreen(
    stats: ListeningStats?,
    onLoad: (ListeningStatsRange) -> Unit,
    onOpenTrack: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    monthlyReport: EchoMonthlyRecap? = null,
    monthlyError: Boolean = false,
    onLoadMonthly: (Int, Int) -> Unit = { _, _ -> },
) {
    var monthlyVisible by rememberSaveable { mutableStateOf(false) }
    val pages = rememberSaveableStateHolder()
    val back = rememberEchoBackProgress()
    fun closeMonthly() { monthlyVisible = false; back.reset() }
    EchoOverlayBackHandler(
        enabled = monthlyVisible,
        onProgress = back::update,
        onCancel = { back.restore() },
        onDismiss = ::closeMonthly,
    )
    EchoPageContent(
        targetState = monthlyVisible,
        contentKey = { if (it) "monthly" else "overview" },
        isBackward = { initial, target -> initial && !target },
        modifier = modifier,
        back = if (monthlyVisible) EchoPageBack(false) { back.value } else null,
        label = "listening-stats-navigation",
    ) { monthly ->
        pages.SaveableStateProvider(if (monthly) "monthly" else "overview") {
            if (monthly) {
                MonthlyRecapScreen(monthlyReport, monthlyError, onLoadMonthly, onBack = ::closeMonthly)
            } else {
                ListeningStatsOverview(stats, onLoad, onOpenTrack, onBack,
                    onOpenMonthly = { monthlyVisible = true })
            }
        }
    }
}
