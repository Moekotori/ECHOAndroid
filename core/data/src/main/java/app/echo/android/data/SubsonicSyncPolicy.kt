package app.echo.android.data

internal data class SubsonicAlbumFallbackPlan(
    /** 需要逐张 getAlbum 拉取的专辑(有变化的 + 轮换刷新窗口内的) */
    val albumsToFetch: List<SubsonicAlbum>,
    /** 被跳过的未变专辑对应的本地曲目 id(标记 seen,免于删除检测) */
    val seenTrackIds: List<String>,
    /** 被跳过的曲目数,计入 scannedCount 以维持完整性判定 */
    val skippedTrackCount: Int,
    val skippedAlbumCount: Int,
)

object SubsonicSyncPolicy {
    const val AlbumFetchConcurrency = 8
    const val PlaylistFetchConcurrency = 4

    /** 每次同步在未变专辑里轮换强刷的组数,用于清洗"曲目数没变但元数据改了"的残留 */
    const val UnchangedAlbumRefreshBudget = 16

    val Search3QueryAttempts = listOf("", "*")

    /** 与 withComputedSearchMetadata 对 getAlbum 歌曲计算 albumKey 的方式保持一致 */
    internal fun albumCandidateKey(album: SubsonicAlbum): String {
        val normalizedArtist = album.artist?.normalizedForSearch()
        return libraryAlbumKey(
            normalizedAlbum = album.name.normalizedForSearch(),
            normalizedAlbumArtist = normalizedArtist,
            normalizedArtist = normalizedArtist,
        )
    }

    /**
     * search3 不可用的回退路径原本对每张专辑都发一次 getAlbum(N+1)。
     * 这里按 albumKey 与本地库比对:曲目数一致的专辑组视为未变,跳过 HTTP 拉取,
     * 只把本地曲目 id 标记为 seen。key 推导不一致(改名、编外 albumArtist 等)
     * 只会导致该专辑照常拉取,不会误跳。
     * 曲目数不变但元数据变化的情况靠轮换窗口(每次同步强刷若干未变组)最终修复。
     */
    internal fun planAlbumFallbackSync(
        albums: List<SubsonicAlbum>,
        localTrackIdsByAlbumKey: Map<String, List<String>>,
        refreshSalt: Long,
    ): SubsonicAlbumFallbackPlan {
        // 同一 albumKey 可能对应多张服务器专辑,跳过/强刷都要按组进行,
        // 否则组内只拉一半会让另一半的本地行被当成缺失而删除
        val groups = albums.groupBy(::albumCandidateKey)
        val changed = ArrayList<SubsonicAlbum>(albums.size)
        val skippableGroups = ArrayList<Map.Entry<String, List<SubsonicAlbum>>>()
        for (entry in groups) {
            val expected = entry.value.sumOf { it.songCount.coerceAtLeast(0) }
            val localIds = localTrackIdsByAlbumKey[entry.key]
            if (expected > 0 && localIds != null && localIds.size == expected) {
                skippableGroups += entry
            } else {
                changed += entry.value
            }
        }
        if (skippableGroups.isEmpty()) {
            return SubsonicAlbumFallbackPlan(
                albumsToFetch = changed,
                seenTrackIds = emptyList(),
                skippedTrackCount = 0,
                skippedAlbumCount = 0,
            )
        }
        skippableGroups.sortBy { it.key }
        val refreshCount = UnchangedAlbumRefreshBudget.coerceAtMost(skippableGroups.size)
        val start = ((refreshSalt % skippableGroups.size).toInt() + skippableGroups.size) %
            skippableGroups.size
        val refreshIndices = (0 until refreshCount).map { (start + it) % skippableGroups.size }.toSet()
        val seenTrackIds = ArrayList<String>()
        var skippedTrackCount = 0
        var skippedAlbumCount = 0
        skippableGroups.forEachIndexed { index, entry ->
            if (index in refreshIndices) {
                changed += entry.value
            } else {
                val localIds = localTrackIdsByAlbumKey.getValue(entry.key)
                seenTrackIds += localIds
                skippedTrackCount += localIds.size
                skippedAlbumCount += entry.value.size
            }
        }
        return SubsonicAlbumFallbackPlan(
            albumsToFetch = changed,
            seenTrackIds = seenTrackIds,
            skippedTrackCount = skippedTrackCount,
            skippedAlbumCount = skippedAlbumCount,
        )
    }

    fun shouldPreferSearch3Bulk(expectedSongCount: Int, bulkSongCount: Int): Boolean {
        if (expectedSongCount <= 0) return false
        if (bulkSongCount <= 0) return false
        return bulkSongCount >= expectedSongCount
    }

    fun shouldAuthorizeMissingRowDeletion(
        usedSearch3: Boolean,
        expectedSongCount: Int,
        bulkSongCount: Int,
        existingRemoteCount: Int,
        hitVisitCap: Boolean = false,
    ): Boolean {
        if (hitVisitCap) return false
        if (!usedSearch3) return true
        if (expectedSongCount <= 0) return false
        if (bulkSongCount < expectedSongCount) return false
        if (existingRemoteCount > 0 && bulkSongCount < existingRemoteCount) return false
        return true
    }

    fun shouldReplaceSyncedPlaylist(
        fetchSucceeded: Boolean,
        remoteSongCount: Int,
        matchedTrackCount: Int,
    ): Boolean {
        if (!fetchSucceeded) return false
        if (remoteSongCount > 0 && matchedTrackCount <= 0) return false
        return true
    }

    internal fun localTrackIdsForAlbum(
        album: SubsonicAlbum,
        localTrackIdsByAlbumKey: Map<String, List<String>>,
    ): List<String> = localTrackIdsByAlbumKey[albumCandidateKey(album)].orEmpty()

    fun shouldRewriteSyncedPlaylist(
        existingName: String?,
        existingTrackIds: List<String>?,
        incomingName: String,
        incomingTrackIds: List<String>,
    ): Boolean {
        if (existingTrackIds == null) return true
        if (existingName != incomingName) return true
        return existingTrackIds != incomingTrackIds
    }
}
