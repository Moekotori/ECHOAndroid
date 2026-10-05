package app.echo.android.feature.player

import app.echo.android.model.playback.EchoRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class QueuePlaybackModeTest {
    @Test
    fun selectingAnyModeFromAnyExistingCombinationSetsBothFlags() {
        EchoRepeatMode.entries.forEach { repeat ->
            listOf(false, true).forEach { shuffle ->
                QueuePlaybackMode.entries.forEach { target ->
                    var actualRepeat = repeat
                    var actualShuffle = shuffle
                    var calls = 0
                    selectQueuePlaybackMode(
                        target, repeat, shuffle,
                        onSetRepeatMode = { actualRepeat = it; calls++ },
                        onToggleShuffle = { actualShuffle = !actualShuffle; calls++ },
                    )
                    assertEquals(target.repeatMode, actualRepeat)
                    assertEquals(target.shuffleEnabled, actualShuffle)
                    assertEquals(
                        (if (repeat != target.repeatMode) 1 else 0) +
                            (if (shuffle != target.shuffleEnabled) 1 else 0),
                        calls,
                    )
                }
            }
        }
    }

    @Test
    fun currentModeShowsSingleRepeatBeforeShuffleAndRoundTripsStandardModes() {
        assertEquals(QueuePlaybackMode.RepeatOne, QueuePlaybackMode.from(EchoRepeatMode.One, true))
        assertEquals(QueuePlaybackMode.Shuffle, QueuePlaybackMode.from(EchoRepeatMode.Off, true))
        QueuePlaybackMode.entries.forEach { mode ->
            assertEquals(mode, QueuePlaybackMode.from(mode.repeatMode, mode.shuffleEnabled))
        }
    }
}
