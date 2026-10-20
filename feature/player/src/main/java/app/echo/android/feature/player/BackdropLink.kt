package app.echo.android.feature.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import app.echo.android.model.settings.EchoLyricsBackdrop

/**
 * What a backdrop may follow without becoming a source of truth: whether audio is playing and which lyric
 * line is current. Both are read lazily inside effects, so position ticks never recompose the page.
 */
@Stable
internal class BackdropLink(
    val playing: State<Boolean>,
    val lineIndex: State<Int>,
)

/** Styles that give the surface a short kick on every lyric line change. */
internal val EchoLyricsBackdrop.reactsToLyrics: Boolean
    get() = this == EchoLyricsBackdrop.Vinyl || this == EchoLyricsBackdrop.Iso

/** Styles whose motion eases down while playback is paused. */
internal val EchoLyricsBackdrop.slowsWhenPaused: Boolean
    get() = this == EchoLyricsBackdrop.Ridge || reactsToLyrics
