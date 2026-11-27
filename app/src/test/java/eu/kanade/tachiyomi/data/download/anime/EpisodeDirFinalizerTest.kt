package eu.kanade.tachiyomi.data.download.anime

import android.text.TextUtils
import com.hippo.unifile.UniFile
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The downloader downloads an episode into `<episode>_tmp` and then moves that directory to its
 * final name. Retries may encounter an existing destination from an older version or a manual copy.
 */
class EpisodeDirFinalizerTest {

    @TempDir
    lateinit var root: File

    @BeforeEach
    fun beforeEach() {
        // RawFile uses TextUtils.isEmpty, which is a stub in local unit tests.
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
    }

    @AfterEach
    fun afterEach() {
        unmockkStatic(TextUtils::class)
    }

    @Test
    fun `Finished episode is moved to its final directory`() {
        writeVideo("Ep 1_tmp", "video")

        val result = finalizeEpisodeDir(animeDir(), child("Ep 1_tmp"), "Ep 1")

        result.shouldNotBeNull().name shouldBe "Ep 1"
        File(root, "Ep 1/Ep 1.mkv").readText() shouldBe "video"
        File(root, "Ep 1_tmp").exists() shouldBe false
    }

    @Test
    fun `Provider rejecting the rename gets a copy`() {
        writeVideo("Ep 1_tmp", "video")
        val tmpDir = spyk(child("Ep 1_tmp"))
        every { tmpDir.renameTo(any()) } throws IllegalStateException("Failed to rename")

        val result = finalizeEpisodeDir(animeDir(), tmpDir, "Ep 1")

        result.shouldNotBeNull().name shouldBe "Ep 1"
        File(root, "Ep 1/Ep 1.mkv").readText() shouldBe "video"
        File(root, "Ep 1_tmp").exists() shouldBe false
    }

    @Test
    fun `An empty directory from an earlier attempt can be replaced`() {
        File(root, "Ep 1").mkdir()
        writeVideo("Ep 1_tmp", "video")
        val tmpDir = spyk(child("Ep 1_tmp"))
        every { tmpDir.renameTo(any()) } returns false

        val result = finalizeEpisodeDir(animeDir(), tmpDir, "Ep 1")

        result.shouldNotBeNull().name shouldBe "Ep 1"
        File(root, "Ep 1/Ep 1.mkv").readText() shouldBe "video"
        File(root, "Ep 1_tmp").exists() shouldBe false
    }

    @Test
    fun `An existing provider directory keeps the finished episode in place`() {
        File(root, "Ep 1").mkdir()
        writeVideo("Ep 1_tmp", "video")
        val animeDir = spyk(animeDir())
        val leftover = spyk(child("Ep 1"))
        every { animeDir.findFile("Ep 1") } returns leftover
        every { leftover.filePath } returns null

        val result = finalizeEpisodeDir(animeDir, child("Ep 1_tmp"), "Ep 1")

        result.shouldBeNull()
        File(root, "Ep 1").isDirectory shouldBe true
        File(root, "Ep 1_tmp/Ep 1.mkv").readText() shouldBe "video"
    }

    @Test
    fun `A destination with an ambiguous copy and user data is preserved with staging`() {
        writeVideo("Ep 1", "possibly incomplete")
        File(root, "Ep 1/Notes.txt").writeText("original notes")
        writeVideo("Ep 1_tmp", "finished transfer")

        val result = finalizeEpisodeDir(animeDir(), child("Ep 1_tmp"), "Ep 1")

        result.shouldBeNull()
        File(root, "Ep 1/Ep 1.mkv").readText() shouldBe "possibly incomplete"
        File(root, "Ep 1/Notes.txt").readText() shouldBe "original notes"
        File(root, "Ep 1_tmp/Ep 1.mkv").readText() shouldBe "finished transfer"
    }

    @Test
    fun `An unreadable destination preserves staging`() {
        File(root, "Ep 1").mkdir()
        writeVideo("Ep 1_tmp", "finished transfer")
        val directory = spyk(animeDir())
        val destination = spyk(child("Ep 1"))
        every { directory.findFile("Ep 1") } returns destination
        every { destination.listFiles() } returns null

        finalizeEpisodeDir(directory, child("Ep 1_tmp"), "Ep 1").shouldBeNull()
        File(root, "Ep 1").isDirectory shouldBe true
        File(root, "Ep 1_tmp/Ep 1.mkv").readText() shouldBe "finished transfer"
    }

    @Test
    fun `A legacy standalone video keeps its extension when renamed`() {
        File(root, "Old episode.mp4").writeText("original")

        child("Old episode.mp4").renameDownloadedEpisode("New episode") shouldBe true

        File(root, "New episode.mp4").readText() shouldBe "original"
        File(root, "Old episode.mp4").exists() shouldBe false
    }

    @Test
    fun `A legacy standalone rename preserves an occupied destination`() {
        File(root, "Old episode.mkv").writeText("original")
        File(root, "New episode.mkv").writeText("existing")

        child("Old episode.mkv").renameDownloadedEpisode("New episode") shouldBe false

        File(root, "Old episode.mkv").readText() shouldBe "original"
        File(root, "New episode.mkv").readText() shouldBe "existing"
    }

    private fun writeVideo(dir: String, content: String) {
        File(root, dir).mkdir()
        File(root, "$dir/Ep 1.mkv").writeText(content)
    }

    private fun animeDir(): UniFile = UniFile.fromFile(root)!!

    // Look files up through the root so they have a parent, as they do in the downloader.
    private fun child(name: String): UniFile = animeDir().findFile(name)!!
}
