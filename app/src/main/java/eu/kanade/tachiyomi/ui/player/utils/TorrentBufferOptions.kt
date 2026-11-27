package eu.kanade.tachiyomi.ui.player.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val MIB = 1024L * 1024

internal fun isTorrentPlaybackUrl(url: String, torrentServerUrl: String): Boolean {
    // Existing sources can also return local URIs or extensionless torrent endpoints.
    if (url.startsWith(torrentServerUrl) || url.startsWith("magnet") || url.endsWith("torrent")) return true
    return url.toHttpUrlOrNull()?.pathSegments?.last()?.endsWith(".torrent", ignoreCase = true) == true
}

internal fun torrentForwardCacheLimitMiB(
    is64Bit: Boolean,
    lowMemory: Boolean,
    totalMemoryBytes: Long,
    availableMemoryBytes: Long,
    lowMemoryThresholdBytes: Long,
): Int? {
    if (!is64Bit || lowMemory || totalMemoryBytes < 3072 * MIB) return null
    // Reserve most of the current headroom for decoding, TorrServer and other Android processes.
    val budget = (availableMemoryBytes - lowMemoryThresholdBytes) / MIB / 4
    return budget.coerceAtMost(320).takeIf { it > 64 }?.toInt()
}

/** Applies opt-in defaults to the current torrent file. */
internal fun torrentBufferOptions(
    options: List<Pair<String, String>>,
    seconds: Int,
    forwardCacheLimitMiB: Int?,
    configuredOptions: Set<String>,
): List<Pair<String, String>> {
    if (seconds <= 0) return options

    val specified = options.mapTo(configuredOptions.toMutableSet()) { (name, _) -> name.removePrefix("no-") }
    val wait = seconds.coerceAtMost(30).toString()
    val defaults = buildList {
        add("cache" to "yes")
        add("cache-pause" to "yes")
        add("cache-pause-initial" to "yes")
        add("cache-pause-wait" to wait)
        if (forwardCacheLimitMiB != null && "demuxer-max-bytes" !in specified && "cache-on-disk" !in specified) {
            add("demuxer-max-bytes" to "${forwardCacheLimitMiB}MiB")
            // Bound read-ahead for our larger cache without shortening a user-configured wait.
            if ("cache-pause-wait" !in specified) add("cache-secs" to wait)
        }
    }
    return options + defaults.filterNot { (name, _) -> name in specified }
}
