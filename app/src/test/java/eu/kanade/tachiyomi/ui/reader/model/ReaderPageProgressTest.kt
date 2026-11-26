package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.source.model.Page
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter

class ReaderPageProgressTest {
    private val chapter = ReaderChapter(Chapter.create().copy(id = 1L))

    @Test
    fun `a final two-page spread advances progress to the last page`() {
        val penultimate = page(index = 8)
        val final = page(index = 9)

        assertSame(final, penultimate.pageForProgress(final))
    }

    @Test
    fun `a failed companion keeps progress on the visible primary page`() {
        val primary = page(index = 0)
        val failed = page(index = 1).apply { status = Page.State.ERROR }

        assertSame(primary, primary.pageForProgress(failed))
    }

    @Test
    fun `an image from another chapter cannot advance this chapter`() {
        val primary = page(index = 0)
        val other = page(index = 1, chapter = ReaderChapter(Chapter.create().copy(id = 2L)))

        assertSame(primary, primary.pageForProgress(other))
    }

    @Test
    fun `single pages and second halves keep the current source index`() {
        val primary = page(index = 0)

        assertSame(primary, primary.pageForProgress(null))
        assertSame(primary, primary.pageForProgress(InsertPage(primary)))
    }

    private fun page(index: Int, chapter: ReaderChapter = this.chapter): ReaderPage = ReaderPage(index).apply {
        this.chapter = chapter
        status = Page.State.READY
    }
}
