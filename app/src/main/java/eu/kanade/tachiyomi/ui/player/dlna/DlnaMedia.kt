package eu.kanade.tachiyomi.ui.player.dlna

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class DlnaMedia private constructor(
    val url: HttpUrl,
    val title: String,
    val mimeType: String,
) {
    companion object {
        fun fromDirectVideo(url: String, title: String, headers: Headers?): DlnaMedia? {
            val media = url.toHttpUrlOrNull() ?: return null
            if (media.username.isNotEmpty() || media.password.isNotEmpty() || media.query != null ||
                media.fragment != null || !media.host.isReachableFromRenderer()
            ) {
                return null
            }
            if (headers?.names()?.any { it.lowercase() !in OPTIONAL_HEADERS } == true) return null
            val extension = media.pathSegments.last().substringAfterLast('.', "").lowercase()
            val mime = when (extension) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                else -> return null
            }
            return DlnaMedia(media, title.trim().take(250), mime)
        }

        private fun String.isReachableFromRenderer(): Boolean {
            val host = trimEnd('.')
            if (host == "localhost" || host.endsWith(".localhost") || host == "::" || host == "::1") return false
            if (host.all { it in '0'..'9' || it == '.' }) {
                val octets = host.split('.').map { it.toIntOrNull() }
                val first = octets.firstOrNull() ?: return false
                return octets.size == 4 && octets.all { it != null && it in 0..255 } &&
                    first in 1..223 && first != 127
            }
            return true
        }

        private val OPTIONAL_HEADERS = setOf("user-agent", "accept", "accept-language", "accept-encoding")
    }
}
