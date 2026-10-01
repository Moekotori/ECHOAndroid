package app.echo.android.feature.player.afterglow

/** Native direction inspired by PC huge / mixed / kanjiFocus / vcols / tyMargin / subtitleBar. */
internal enum class AfterglowTypography(
    val sizeScale: Float, val widthFraction: Float, val anchorY: Float, val tilt: Float,
) {
    Hero(1.65f, 1f, 0.47f, -2.4f),
    Staggered(1.06f, 0.90f, 0.46f, -1.2f),
    Focus(1.18f, 0.94f, 0.53f, 0f),
    Vertical(1.10f, 1f, 0.46f, 0f),
    Margin(0.90f, 0.91f, 0.61f, 0f),
    Subtitle(1.08f, 1f, 0.58f, 0f),
    ;

    companion object {
        fun choose(glyphCount: Int, verticalSafe: Boolean, composition: Int, lightweight: Boolean): AfterglowTypography {
            if (lightweight) return Subtitle
            if (glyphCount <= 5) return Hero
            val choices = when {
                verticalSafe && glyphCount <= 12 -> arrayOf(Hero, Focus, Vertical, Margin, Subtitle)
                glyphCount <= 16 -> arrayOf(Hero, Focus, Staggered, Margin, Subtitle)
                else -> arrayOf(Staggered, Margin, Subtitle, Focus)
            }
            return choices[Math.floorMod(composition, choices.size)]
        }

        fun emphasis(text: String): String? {
            for (keyword in arrayOf("光", "夢", "梦", "声", "君", "星", "夜", "愛", "爱", "心", "空", "花", "雨", "風", "风")) {
                if (text.contains(keyword)) return keyword
            }
            return null
        }
    }
}
