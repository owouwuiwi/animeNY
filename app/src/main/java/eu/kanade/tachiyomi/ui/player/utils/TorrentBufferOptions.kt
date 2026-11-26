package eu.kanade.tachiyomi.ui.player.utils

/** Applies only to the current torrent file and leaves stream options intact when disabled. */
internal fun torrentBufferOptions(options: List<Pair<String, String>>, seconds: Int): List<Pair<String, String>> {
    if (seconds <= 0) return options
    val bufferOptions = mapOf(
        "cache" to "yes",
        "cache-pause" to "yes",
        "cache-pause-initial" to "yes",
        "cache-pause-wait" to seconds.coerceAtMost(30).toString(),
    )
    return options.filterNot { (name, _) -> name in bufferOptions } + bufferOptions.toList()
}
