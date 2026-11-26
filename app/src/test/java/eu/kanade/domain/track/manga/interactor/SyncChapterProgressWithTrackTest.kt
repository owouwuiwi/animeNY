package eu.kanade.domain.track.manga.interactor

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.MangaTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.model.MangaTrack

class SyncChapterProgressWithTrackTest {
    private val updateChapter = mockk<UpdateChapter>()
    private val insertTrack = mockk<InsertMangaTrack>()
    private val getChapters = mockk<GetChaptersByMangaId>()
    private val tracker = mockk<MangaTracker>()
    private val preferences = TrackPreferences(InMemoryPreferenceStore())
    private val sync = SyncChapterProgressWithTrack(updateChapter, insertTrack, getChapters, preferences)
    private val remoteTrack = mockk<MangaTrack> {
        every { lastChapterRead } returns 2.0
    }

    @Test
    fun `remote progress import is opt in`() = runBlocking {
        sync.await(1, remoteTrack, tracker)

        coVerify(exactly = 0) { getChapters.await(any(), any()) }
        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
    }

    @Test
    fun `marks only unread numbered chapters up to remote progress`() = runBlocking {
        preferences.importTrackerProgress.set(true)
        val chapter = Chapter.create()
        coEvery { getChapters.await(1, false) } returns listOf(
            chapter.copy(id = 1, chapterNumber = 1.0, read = true),
            chapter.copy(id = 2, chapterNumber = 2.0, lastPageRead = 7, bookmark = true),
            chapter.copy(id = 3, chapterNumber = 3.0),
            chapter.copy(id = 4, chapterNumber = -1.0),
        )
        val updates = slot<List<ChapterUpdate>>()
        coEvery { updateChapter.awaitAll(capture(updates)) } returns Unit

        sync.await(1, remoteTrack, tracker)

        val updated = updates.captured.single()
        assertEquals(2L, updated.id)
        assertEquals(true, updated.read)
        assertNull(updated.lastPageRead)
        assertNull(updated.bookmark)
        coVerify(exactly = 0) { tracker.update(any(), any()) }
    }
}
