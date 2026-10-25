package app.echo.android.model.radio

/** Only verified broken directory URLs are substituted; custom station artwork is preserved. */
object RadioArtworkPolicy {
    fun corrected(url: String?): String? = when (url) {
        "https://www.europe1.fr/build/e1/images/favicon-120x120.png" ->
            "https://db.radioline.fr/pictures/radio_fa64fea9b6ccd92e058ced3fa41f58f5/logo200.jpg"
        "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a0/France_Inter_logo_2021.svg/1200px-France_Inter_logo_2021.svg.png" ->
            "https://www.radiofrance.fr/_app/immutable/assets/franceinter-seosquare.B8eFKW6V.png"
        "https://img.nrj.fr/J6jlakQsQ2siRzCzCI_2IDCQj1E=/80x80/https%3A%2F%2Fplayers.nrjaudio.fm%2Flive-metadata%2Fplayer%2Fimg%2Fplayer-files%2Fnosta%2Flogos%2F640x640%2FP_Nostalgie_Premium_Logo_2023.png" ->
            "https://players.nrjaudio.fm/live-metadata/player/img/player-files/nosta/logos/640x640/nostalgie_en_direct_sans_point_1400.png"
        else -> url
    }
}
