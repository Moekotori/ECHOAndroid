package app.echo.android.model.settings

enum class EchoWidthSizeClass {
    Compact,
    Medium,
    Expanded,
    ;

    val prefersLibrarySplit: Boolean
        get() = this == Expanded

    val prefersNowPlayingSplit: Boolean
        get() = this == Expanded

    fun contentMaxWidthDp(): Int =
        when (this) {
            Compact -> CompactContentMaxWidthDp
            Medium -> MediumContentMaxWidthDp
            Expanded -> ExpandedContentMaxWidthDp
        }

    companion object {
        const val MediumMinWidthDp = 600
        const val ExpandedMinWidthDp = 840
        const val CompactContentMaxWidthDp = 560
        const val MediumContentMaxWidthDp = 720
        const val ExpandedContentMaxWidthDp = 960

        fun fromWidthDp(widthDp: Int): EchoWidthSizeClass =
            when {
                widthDp < MediumMinWidthDp -> Compact
                widthDp < ExpandedMinWidthDp -> Medium
                else -> Expanded
            }
    }
}
