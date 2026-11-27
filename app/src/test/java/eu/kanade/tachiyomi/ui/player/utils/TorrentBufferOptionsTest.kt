package eu.kanade.tachiyomi.ui.player.utils

import aniyomi.core.common.torrent.isTorrentDownloadUrl
import aniyomi.core.common.torrent.isTorrentPlaybackUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TorrentBufferOptionsTest {
    private val serverUrl = "http://127.0.0.1:8090"

    @Test
    fun `Playback and downloads recognize torrent paths with a query or fragment`() {
        val urls = listOf(
            "https://example.test/clip.torrent?signature=opaque%2Bvalue&download=1",
            "http://example.test/clip%2ETORRENT#download",
        )
        for (url in urls) {
            assertTrue(isTorrentPlaybackUrl(url, serverUrl), url)
            assertTrue(isTorrentDownloadUrl(url, serverUrl), url)
        }
    }

    @Test
    fun `Existing torrent links still use the torrent server`() {
        val urls = listOf(
            "magnet:?xt=urn:btih:test&index=1",
            "$serverUrl/stream?index=1",
            "content://downloads/document/clip.torrent",
            "file:///storage/clip.torrent",
        )
        for (url in urls) {
            assertTrue(isTorrentPlaybackUrl(url, serverUrl), url)
            assertTrue(isTorrentDownloadUrl(url, serverUrl), url)
        }
    }

    @Test
    fun `Extensionless legacy endpoints remain a player override`() {
        val url = "https://example.test/download/torrent"
        assertTrue(isTorrentPlaybackUrl(url, serverUrl))
        assertFalse(isTorrentDownloadUrl(url, serverUrl))
    }

    @Test
    fun `Other media links do not use the torrent server`() {
        val urls = listOf(
            "https://example.test/clip.mp4?filename=clip.torrent&download=1",
            "https://example.test/clip.torrent/main.m3u8",
            "file:///storage/clip.TORRENT",
        )
        for (url in urls) {
            assertFalse(isTorrentPlaybackUrl(url, serverUrl), url)
            assertFalse(isTorrentDownloadUrl(url, serverUrl), url)
        }
    }

    @Test
    fun `Forward cache grows with current memory headroom up to its ceiling`() {
        assertEquals(320, forwardCacheLimit())
        assertEquals(150, forwardCacheLimit(availableMiB = 1112))
        assertNull(forwardCacheLimit(availableMiB = 768))
        assertNull(forwardCacheLimit(availableMiB = 128))
    }

    @Test
    fun `Keeps the normal size on constrained devices`() {
        assertNull(forwardCacheLimit(is64Bit = false))
        assertNull(forwardCacheLimit(lowMemory = true))
        assertNull(forwardCacheLimit(totalMiB = 3071, availableMiB = 2800))
    }

    @Test
    fun `Off preserves the supplied options`() {
        val options = listOf("cache" to "auto", "demuxer-max-back-bytes" to "16MiB")
        assertSame(options, torrentBufferOptions(options, 0, 320, emptySet()))
    }

    @Test
    fun `Caps both wait and read-ahead for the larger forward cache`() {
        val options = torrentBufferOptions(emptyList(), 90, 320, emptySet()).toMap()
        assertEquals(
            mapOf(
                "cache" to "yes",
                "cache-pause" to "yes",
                "cache-pause-initial" to "yes",
                "cache-pause-wait" to "30",
                "demuxer-max-bytes" to "320MiB",
                "cache-secs" to "30",
            ),
            options,
        )
    }

    @Test
    fun `Prebuffer timing still applies without a larger cache`() {
        val options = torrentBufferOptions(emptyList(), 15, null, emptySet()).toMap()
        assertEquals("15", options["cache-pause-wait"])
        assertFalse("demuxer-max-bytes" in options)
        assertFalse("cache-secs" in options)
    }

    @Test
    fun `Preserves explicit cache options from the video and mpv conf`() {
        val videoOptions = listOf(
            "no-cache" to "yes",
            "demuxer-max-bytes" to "80MiB",
            "demuxer-max-back-bytes" to "16MiB",
            "cache-pause-initial" to "no",
        )
        val options = torrentBufferOptions(videoOptions, 5, 320, setOf("cache-pause-wait"))
        assertEquals(videoOptions + ("cache-pause" to "yes"), options)
    }

    @Test
    fun `The automatic cache duration cannot shorten an explicit wait`() {
        val options = torrentBufferOptions(emptyList(), 5, 320, setOf("cache-pause-wait")).toMap()
        assertEquals("320MiB", options["demuxer-max-bytes"])
        assertFalse("cache-secs" in options)
    }

    @Test
    fun `An explicit disk cache or forward budget owns its own sizing`() {
        for (configured in listOf("cache-on-disk", "demuxer-max-bytes")) {
            val options = torrentBufferOptions(emptyList(), 15, 320, setOf(configured)).toMap()
            assertFalse("demuxer-max-bytes" in options)
            assertFalse("cache-secs" in options)
        }
    }

    private fun forwardCacheLimit(
        is64Bit: Boolean = true,
        lowMemory: Boolean = false,
        totalMiB: Long = 8192,
        availableMiB: Long = 4096,
    ): Int? {
        val mib = 1024L * 1024
        return torrentForwardCacheLimitMiB(
            is64Bit,
            lowMemory,
            totalMemoryBytes = totalMiB * mib,
            availableMemoryBytes = availableMiB * mib,
            lowMemoryThresholdBytes = 512 * mib,
        )
    }
}
