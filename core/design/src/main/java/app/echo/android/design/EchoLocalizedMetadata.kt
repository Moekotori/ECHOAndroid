package app.echo.android.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Localize only missing metadata and known scanner sentinels, never actual music tags. */
@Composable
fun displayArtist(value: String?): String =
    displayMetadataOrUnknown(value, stringResource(R.string.metadata_unknown_artist))

@Composable
fun displayAlbum(value: String?): String =
    displayMetadataOrUnknown(value, stringResource(R.string.metadata_unknown_album))

@Composable
fun displayTrack(value: String?): String =
    displayMetadataOrUnknown(value, stringResource(R.string.metadata_unknown_track))
