package com.lumera.app.ui.details

import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.domain.episodePlaybackId
import java.net.URLEncoder

val TORRENT_TRACKERS = listOf(
    // HTTP trackers (TCP — work even when UDP is blocked)
    "http://tracker.opentrackr.org:1337/announce",
    "http://tracker.openbittorrent.com:80/announce",
    "http://tracker1.bt.moack.co.kr:80/announce",
    "http://tracker.gbitt.info:80/announce",
    // UDP trackers (fallback)
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
    "udp://exodus.desync.com:6969/announce"
)

fun extractPrimaryYear(releaseInfo: String?): String {
    if (releaseInfo.isNullOrBlank()) return "----"
    return Regex("\\d{4}").find(releaseInfo)?.value ?: releaseInfo.take(4)
}

fun findFirstEpisode(videos: List<MetaVideo>?): MetaVideo? {
    if (videos.isNullOrEmpty()) return null

    val numbered = videos.filter { it.season > 0 && it.episode > 0 }
    val candidates = if (numbered.isNotEmpty()) numbered else videos
    return candidates.minWithOrNull(
        compareBy<MetaVideo>({ if (it.season > 0) it.season else Int.MAX_VALUE })
            .thenBy { if (it.episode > 0) it.episode else Int.MAX_VALUE }
            .thenBy { it.title }
    )
}

fun resolveEpisodeForPlaybackId(
    seriesId: String,
    videos: List<MetaVideo>?,
    playbackId: String?
): MetaVideo? {
    val targetId = playbackId ?: return null
    val episodeList = videos ?: return null
    // Exact match (new format: seriesId:season:episode)
    episodeList.firstOrNull { episodePlaybackId(seriesId, it) == targetId }?.let { return it }
    // Fallback: match by season/episode numbers (handles old-format entries)
    val parsed = parseSeasonEpisodeFromPlaybackId(targetId) ?: return null
    return episodeList.firstOrNull { it.season == parsed.first && it.episode == parsed.second }
}

fun parseSeasonEpisodeFromPlaybackId(playbackId: String?): Pair<Int, Int>? {
    val id = playbackId ?: return null
    val parts = id.split(":")
    if (parts.size < 3) return null
    val season = parts[parts.lastIndex - 1].toIntOrNull() ?: return null
    val episode = parts.last().toIntOrNull() ?: return null
    if (season <= 0 || episode <= 0) return null
    return season to episode
}

fun playbackIdBelongsToSeries(seriesId: String, playbackId: String): Boolean {
    val parts = playbackId.split(":")
    if (parts.size < 3) return playbackId == seriesId
    val season = parts[parts.lastIndex - 1].toIntOrNull()
    val episode = parts.last().toIntOrNull()
    if (season == null || episode == null) return playbackId == seriesId
    return parts.dropLast(2).joinToString(":") == seriesId
}

fun resolvePlayableUrl(stream: com.lumera.app.data.model.stremio.Stream): String? {
    if (!stream.url.isNullOrEmpty()) return stream.url
    if (!stream.infoHash.isNullOrEmpty()) {
        val trackerParams = TORRENT_TRACKERS.joinToString("") {
            "&tr=${URLEncoder.encode(it, "UTF-8")}"
        }
        return "magnet:?xt=urn:btih:${stream.infoHash}&dn=Video${trackerParams}"
    }
    return null
}

fun parseAgeRating(rating: String?): Int {
    if (rating.isNullOrBlank()) return 0
    val upper = rating.uppercase().trim()
    
    // First try standard matches to handle non-pure-digit cases properly
    if (upper.contains("PG-13") || upper.contains("13")) return 13
    if (upper.contains("PG-14") || upper.contains("14")) return 14
    if (upper.contains("PG-16") || upper.contains("16")) return 16
    if (upper.contains("PG-18") || upper.contains("18")) return 18
    if (upper == "G" || upper == "U" || upper == "L" || upper == "TP" || upper == "ALL") return 0
    if (upper.contains("PG") || upper == "10") return 10
    if (upper == "R" || upper.contains("15") || upper == "MA15+") return 15
    if (upper == "NC-17" || upper == "X") return 18
    
    // Fallback: extract digits
    val digitsOnly = rating.filter { it.isDigit() }
    if (digitsOnly.isNotEmpty()) {
        return digitsOnly.toIntOrNull() ?: 0
    }
    return 0
}
