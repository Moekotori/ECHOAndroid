package app.echo.android.ui.playback

import android.net.Uri
import androidx.compose.runtime.Composable
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.player.LyricsImportPreviewDialog

@Composable
internal fun EchoLyricsImportPreviewHost(trackId: String, uri: String, viewModel: EchoAndroidViewModel, onDismiss: () -> Unit) {
    LyricsImportPreviewDialog(
        sourceKey = "$trackId:$uri",
        loadPreview = { encoding -> viewModel.previewLyricsImport(Uri.parse(uri), encoding) },
        onImport = { lyrics -> viewModel.importLyricsDocument(trackId, lyrics); onDismiss() },
        onDismiss = onDismiss,
    )
}
