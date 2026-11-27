package eu.kanade.tachiyomi.data.download.anime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class EpisodeVideoCopyTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `copy reports progress and writes the entire staged video`() = runBlocking {
        val bytes = ByteArray(180_000) { it.toByte() }
        val staging = File(directory, "episode.tmp").apply { writeBytes(bytes) }
        val destination = ByteArrayOutputStream()
        var progress = 0

        copyDownloadedEpisodeVideo(staging, { destination }) { progress++ }

        assertArrayEquals(bytes, destination.toByteArray())
        assertTrue(progress > 1)
    }

    @Test
    fun `a provider write failure reaches the queue coroutine and closes the output`() {
        val staging = File(directory, "episode.tmp").apply { writeText("video") }
        var closed = false
        val provider = object : OutputStream() {
            override fun write(value: Int) = throw IOException("Provider denied the write")

            override fun close() {
                closed = true
            }
        }

        val failure = assertThrows(IOException::class.java) {
            runBlocking {
                copyDownloadedEpisodeVideo(staging, { provider }) {}
            }
        }

        assertEquals("Provider denied the write", failure.message)
        assertTrue(closed)
    }

    @Test
    fun `cancelling an attempt interrupts the provider copy before the coroutine ends`() = runBlocking {
        val staging = File(directory, "episode.tmp").apply { writeText("video") }
        val writing = CompletableDeferred<Unit>()
        val closed = AtomicBoolean(false)
        val provider = object : OutputStream() {
            override fun write(value: Int) {
                writing.complete(Unit)
                Thread.sleep(10_000)
            }

            override fun close() {
                closed.set(true)
            }
        }
        val copy = launch(Dispatchers.IO) {
            copyDownloadedEpisodeVideo(staging, { provider }) {}
        }

        withTimeout(5_000) {
            writing.await()
            copy.cancelAndJoin()
        }

        assertTrue(closed.get())
    }
}
