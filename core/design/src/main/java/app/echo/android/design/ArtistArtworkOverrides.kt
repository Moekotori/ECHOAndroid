package app.echo.android.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.echo.android.model.library.EchoArtistArtwork

val LocalArtistArtworkOverrides = staticCompositionLocalOf<Map<String, EchoArtistArtwork>> { emptyMap() }

@Composable
fun artistArtworkOverride(name: String): String? = if (name.length > 512) null else LocalArtistArtworkOverrides.current[EchoArtistArtwork.key(name)]?.preferredUri

@Composable
fun artistArtwork(name: String, fallback: String?): String? = artistArtworkOverride(name) ?: fallback
