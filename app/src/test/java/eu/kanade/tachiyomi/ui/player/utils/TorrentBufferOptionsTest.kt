package eu.kanade.tachiyomi.ui.player.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TorrentBufferOptionsTest {
    private val serverUrl = "http://127.0.0.1:8090"

    @Test
    fun `Recognizes torrent paths with a query or fragment`() {
        assertTrue(isTorrentPlaybackUrl("https://example.test/clip.torrent?download=1", serverUrl))
        assertTrue(isTorrentPlaybackUrl("http://example.test/clip%2ETORRENT#download", serverUrl))
    }

    @Test
    fun `Existing torrent links still use the torrent server`() {
        assertTrue(isTorrentPlaybackUrl("https://example.test/download/torrent", serverUrl))
        assertTrue(isTorrentPlaybackUrl("magnet:?xt=urn:btih:test&index=1", serverUrl))
        assertTrue(isTorrentPlaybackUrl("$serverUrl/stream?index=1", serverUrl))
        assertTrue(isTorrentPlaybackUrl("content://downloads/document/clip.torrent", serverUrl))
        assertTrue(isTorrentPlaybackUrl("file:///storage/clip.torrent", serverUrl))
    }

    @Test
    fun `Other media links do not use the torrent server`() {
        assertFalse(isTorrentPlaybackUrl("https://example.test/clip.mp4?filename=clip.torrent&download=1", serverUrl))
        assertFalse(isTorrentPlaybackUrl("https://example.test/clip.torrent/main.m3u8", serverUrl))
    }
}
