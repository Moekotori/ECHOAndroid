package app.echo.android.feature.player.afterglow

import app.echo.android.feature.player.R
import app.echo.android.model.settings.EchoLyricsPageStyle

/** PC scene identifiers stay recognizable; no scene contract is exposed outside the player module. */
internal enum class AfterglowScene(val pcId: String, val title: Int, val night: Boolean) {
    MistRidge("echoMistRidge", R.string.afterglow_scene_ridge, false),
    Wisteria("echoWisteria", R.string.afterglow_scene_wisteria, false),
    LakeMist("echoLakeMist", R.string.afterglow_scene_lake, false),
    Voyage("echoVoyage", R.string.afterglow_scene_voyage, true),
    Aurora("echoAurora", R.string.afterglow_scene_aurora, true),
    StarChart("echoStarChart", R.string.afterglow_scene_chart, true);

    companion object {
        private val mist = listOf(MistRidge, Wisteria, LakeMist)
        private val night = listOf(Voyage, Aurora, StarChart)
        fun forStyle(style: EchoLyricsPageStyle): List<AfterglowScene> =
            if (style == EchoLyricsPageStyle.AfterglowNight) night else mist
        fun automatic(style: EchoLyricsPageStyle, index: Int): AfterglowScene {
            val choices = forStyle(style)
            return choices[(index.coerceAtLeast(0) / 2) % choices.size]
        }
    }
}
