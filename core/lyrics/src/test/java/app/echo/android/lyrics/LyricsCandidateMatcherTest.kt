package app.echo.android.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsCandidateMatcherTest {
    @Test fun partialTitlesAreManualOnlyAndArtistSubstringsAreRejected() {
        val match = requireNotNull(LyricsCandidateMatcher(EchoLyricsSearchRequest("光", "歌手甲", durationMs = 180000))
            .match("光年之外", "歌手甲", null, 180000))
        assertFalse(match.automatic)
        assertNull(LyricsCandidateMatcher(EchoLyricsSearchRequest("Hello", "Ann", durationMs = 180000))
            .match("Hello", "Joanne", null, 180000))
    }

    @Test fun requiresSupportingMetadataAndDoesNotRewardEmptyAlbum() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", "Album"))
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "", 0)).automatic)
        assertTrue(requireNotNull(matcher.match("Song", "Artist", "Album", 0)).automatic)
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "Other", 0)).automatic)
    }

    @Test fun normalizesWidthAndCollaborationOrderWithoutSplittingNamesAtSpaces() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Ｓｏｎｇ", "Alice / Bob", durationMs = 180000))
        assertTrue(requireNotNull(matcher.match("Song", "Bob & Alice", null, 180000)).fast)
        assertNull(LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Ann", durationMs = 180000))
            .match("Song", "Mary Ann", null, 180000))
    }

    @Test fun keepsDifferentVersionsOutEvenWithMatchingDuration() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", durationMs = 180000))
        listOf("Acoustic", "Demo", "Radio Edit", "TV Size", "Live", "Remix", "Instrumental", "伴奏", "现场")
            .forEach { assertNull(it, matcher.match("Song ($it)", "Artist", null, 180000)) }
        // "live" inside an ordinary word must not be classified as a live recording.
        assertNotNull(LyricsCandidateMatcher(EchoLyricsSearchRequest("Alive", "Artist", durationMs = 180000))
            .match("Alive Again", "Artist", null, 180000))
    }

    @Test fun durationBoundariesAndAlbumConflictsLimitAutomaticSelection() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", "Album", 180000))
        assertTrue(requireNotNull(matcher.match("Song", "Artist", "Album", 183000)).fast)
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "Album", 183001)).fast)
        assertTrue(requireNotNull(matcher.match("Song", "Artist", "Album", 188000)).automatic)
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "Album", 188001)).automatic)
        assertFalse(requireNotNull(matcher.match("Song", "Artist", "Other", 184000)).automatic)
        assertNull(matcher.match("Song", "Artist", "Album", 195001))
    }

    @Test fun rejectsExplicitAlbumLanguageConflictsEvenAtIdenticalDuration() {
        val japaneseLabels = listOf("Japanese Ver.", "ＪＰＮ Ｖｅｒ．", "日本語版", "日语版", "일본어 버전")
        val koreanLabels = listOf("Korean Version", "KR ver.", "韓国語版", "韩语版", "한국어 버전")
        for (japanese in japaneseLabels) for (korean in koreanLabels) {
            val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", "EP ($japanese)", 180000))
            assertNull("$japanese vs $korean", matcher.match("Song", "Artist", "EP ($korean)", 180000))
        }
        val korean = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", "EP (한국어 버전)", 180000))
        assertNull(korean.match("Song", "Artist", "EP (Japanese Ver.)", 180000))
    }

    @Test fun languageLabelAliasesRemainCompatibleAndUnmarkedAlbumsAreNotInventedConflicts() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", "EP (Japanese Ver.)", 180000))
        assertTrue(requireNotNull(matcher.match("Song", "Artist", "EP (日本語版)", 180000)).automatic)
        assertTrue(requireNotNull(matcher.match("Song", "Artist", "EP", 180000)).automatic)
        val edition = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song", "Artist", "Japan Edition", 180000))
        assertTrue(requireNotNull(edition.match("Song", "Artist", "Korea Edition", 180000)).automatic)
    }

    @Test fun trackLanguageOverridesAlbumLabel() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("Song (Japanese Ver.)", "Artist", "EP (Korean Ver.)", 180000))
        assertTrue(requireNotNull(matcher.match("Song (Japanese Ver.)", "Artist", "EP (Japanese Ver.)", 180000)).automatic)
    }


    @Test fun acceptsExplicitBilingualNamesAndProviderAliases() {
        val bilingual = LyricsCandidateMatcher(EchoLyricsSearchRequest("봄날 (Spring Day)", "아이유 (IU)", "Album", 180000))
        assertTrue(requireNotNull(bilingual.match("봄날", "IU", "Album", 180000)).fast)
        val original = LyricsCandidateMatcher(EchoLyricsSearchRequest("夜に駆ける", "歌手甲", "Album", 180000))
        assertTrue(requireNotNull(original.match("Yoru ni Kakeru", "Artist A", "Album", 180000,
            titleAliases = listOf("夜に駆ける"), artistAliases = listOf(listOf("Artist A", "歌手甲")))).fast)
        assertNull(original.match("Yoru ni Kakeru", "Artist A", "Album", 180000))
    }

    @Test fun collaborationAliasesKeepEveryMemberAndCvCreditIsManualOnly() {
        val duet = LyricsCandidateMatcher(EchoLyricsSearchRequest("曲", "歌手甲 × 歌手乙", "Album", 180000))
        assertTrue(requireNotNull(duet.match("曲", "歌手乙 / 歌手甲", "Album", 180000)).fast)
        assertFalse(requireNotNull(duet.match("曲", "歌手甲", "Album", 180000)).automatic)
        val character = LyricsCandidateMatcher(EchoLyricsSearchRequest("曲", "キャラ (CV. 声優甲)", "Album", 180000))
        assertFalse(requireNotNull(character.match("曲", "声優甲", "Album", 180000)).automatic)
        assertTrue(requireNotNull(character.match("曲", "キャラ (CV. 声優甲)", "Album", 180000)).automatic)
    }

    @Test fun nativeVersionLabelsCannotBecomeBilingualAliases() {
        val matcher = LyricsCandidateMatcher(EchoLyricsSearchRequest("曲", "歌手", "Album", 180000))
        listOf("カラオケ", "ライブ", "ライヴ", "라이브", "리믹스", "어쿠스틱", "데모", "TVサイズ", "Off Vocal")
            .forEach { assertNull(it, matcher.match("曲 ($it)", "歌手", "Album", 180000)) }
        assertFalse(requireNotNull(matcher.match("曲 (Something)", "歌手", "Album", 195000)).automatic)
    }

}
