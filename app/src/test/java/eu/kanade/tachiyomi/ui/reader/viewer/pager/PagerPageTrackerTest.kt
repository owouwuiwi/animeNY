package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.data.database.models.manga.Chapter
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PagerPageTrackerTest {
    @Test
    fun updatesSelectionWhenTheSecondPageChanges() {
        val activity = mockk<ReaderActivity>(relaxed = true)
        val chapter = ReaderChapter(mockk<Chapter>(relaxed = true))
        val page = ReaderPage(0).apply { this.chapter = chapter }
        val otherPage = ReaderPage(1).apply { this.chapter = chapter }
        chapter.state = ReaderChapter.State.Loaded(listOf(page, otherPage))
        var paired: ReaderPage? = null
        val tracker = PagerPageTracker(
            activity = activity,
            itemAt = { page to paired },
            currentChapter = { chapter },
            nextTransition = { null },
            setChaptersNow = {},
            onPageHolderSelected = { _, _ -> },
        )

        tracker.onPageChange(0)
        paired = otherPage
        tracker.onPageChange(0)
        tracker.onPageChange(0)
        paired = null
        tracker.onPageChange(0)

        verify(exactly = 2) { activity.onPageSelected(page, null) }
        verify(exactly = 1) { activity.onPageSelected(page, otherPage) }
    }

    @Test
    fun appliesLatestChapterAfterPagerBecomesIdle() {
        val activity = mockk<ReaderActivity>(relaxed = true)
        val first = ViewerChapters(ReaderChapter(mockk<Chapter>(relaxed = true)), null, null)
        val latest = ViewerChapters(ReaderChapter(mockk<Chapter>(relaxed = true)), null, null)
        val applied = mutableListOf<ViewerChapters>()
        val tracker = PagerPageTracker(
            activity = activity,
            itemAt = { null },
            currentChapter = { null },
            nextTransition = { null },
            setChaptersNow = applied::add,
            onPageHolderSelected = { _, _ -> },
        )

        tracker.isIdle = false
        tracker.setChapters(first)
        tracker.setChapters(latest)
        assertEquals(emptyList<ViewerChapters>(), applied)
        tracker.isIdle = true

        assertEquals(listOf(latest), applied)
    }
}
