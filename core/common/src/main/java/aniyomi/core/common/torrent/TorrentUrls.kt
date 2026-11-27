package aniyomi.core.common.torrent

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// The player also accepts legacy links ending in an extensionless torrent endpoint.
fun isTorrentPlaybackUrl(url: String, torrentServerUrl: String): Boolean =
    url.endsWith("torrent") || isTorrentEndpoint(url, torrentServerUrl)

fun isTorrentDownloadUrl(url: String, torrentServerUrl: String): Boolean =
    url.endsWith(".torrent") || isTorrentEndpoint(url, torrentServerUrl)

private fun isTorrentEndpoint(url: String, torrentServerUrl: String): Boolean =
    url.startsWith(torrentServerUrl) || url.startsWith("magnet") ||
        url.toHttpUrlOrNull()?.pathSegments?.last()?.endsWith(".torrent", ignoreCase = true) == true
