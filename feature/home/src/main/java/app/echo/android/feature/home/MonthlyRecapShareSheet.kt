package app.echo.android.feature.home

import app.echo.android.design.EchoModalSheet
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoMonthlyRecap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthlyRecapShareSheet(report: EchoMonthlyRecap, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var style by rememberSaveable { mutableStateOf(MonthlyRecapCardStyle.Paper) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var pendingExportFile by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val labels = MonthlyRecapCardLabels(
        YearMonth.of(report.year, report.month).format(DateTimeFormatter.ofPattern("LLLL yyyy", context.resources.configuration.locales[0])),
        stringResource(if (report.partial) R.string.recap_partial else R.string.recap_title),
        stringResource(R.string.recap_minutes_value, report.listenedMs / 60000),
        stringResource(R.string.recap_totals_value, report.totalPlays, report.activeDays),
        stringResource(R.string.recap_albums), stringResource(R.string.recap_new_artists, report.newArtistCount),
        stringResource(R.string.recap_hours), stringResource(R.string.recap_card_basis),
    )
    val bitmap by produceState<Bitmap?>(null, report, style, labels) {
        value = null; failed = false
        try {
            val covers = report.topAlbums.take(3).map { entry ->
                entry.artworkUri?.let { uri ->
                    try {
                        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(uri).size(256).allowHardware(false).build())
                        (result.drawable as? BitmapDrawable)?.bitmap
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
            }
            value = withContext(Dispatchers.Default) { MonthlyRecapCardRenderer.render(report, covers, style, labels) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val ready = pendingExportFile
        pendingExportFile = null
        if (uri != null && ready != null) scope.launch {
            busy = true; failed = false
            try { saveMonthlyRecapImage(context, java.io.File(ready), uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    EchoModalSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.recap_share), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthlyRecapCardStyle.entries.forEach { option -> FilterChip(style == option, onClick = { if (!busy) style = option },
                    label = { Text(stringResource(if (option == MonthlyRecapCardStyle.Paper) R.string.recap_card_paper else R.string.recap_card_midnight)) }) }
            }
            val image = bitmap
            if (image == null && !failed) CircularProgressIndicator()
            else if (image != null) Image(image.asImageBitmap(), stringResource(R.string.recap_card_preview),
                Modifier.fillMaxWidth().aspectRatio(1080f / 1350f))
            if (failed) Text(stringResource(R.string.recap_share_error), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (image != null) scope.launch {
                    busy = true; failed = false
                    try {
                        pendingExportFile = prepareMonthlyRecapFile(context, image).absolutePath
                        export.launch("ECHO-${report.year}-${report.month}.png")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                } }, enabled = image != null && !busy) { Text(stringResource(R.string.recap_export)) }
                Button(onClick = { if (image != null) scope.launch {
                    busy = true; failed = false
                    try { context.startActivity(android.content.Intent.createChooser(monthlyRecapShareIntent(context, image), context.getString(R.string.recap_share))) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                } }, enabled = image != null && !busy) { Text(stringResource(R.string.recap_share)) }
            }
        }
    }
}
