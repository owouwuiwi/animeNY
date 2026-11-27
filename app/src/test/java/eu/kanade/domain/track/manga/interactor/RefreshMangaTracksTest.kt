package eu.kanade.domain.track.manga.interactor

import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import java.io.IOException
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack as DbMangaTrack

class RefreshMangaTracksTest {
    private val preferences = TrackPreferences(InMemoryPreferenceStore())
    private val getTracks = mockk<GetMangaTracks>()
    private val trackerManager = mockk<TrackerManager>()
    private val insertTrack = mockk<InsertMangaTrack>(relaxUnitFun = true)
    private val getChapters = mockk<GetChaptersByMangaId>()
    private val updateChapter = mockk<UpdateChapter>()
    private val sync = SyncChapterProgressWithTrack(updateChapter, insertTrack, getChapters, preferences)
    private val refresh = RefreshMangaTracks(getTracks, trackerManager, insertTrack, sync, preferences)

    @Test
    fun `manual progress import is opt in`() = runTest {
        assertTrue(refresh.await(1, standardProgressOnly = true).isEmpty())

        coVerify(exactly = 0) { getTracks.await(any<Long>()) }
    }

    @Test
    fun `manual progress import refreshes standard trackers without uploading reading progress`() = runTest {
        preferences.importTrackerProgress.set(true)
        val standardBinding = binding(1)
        val standard = standardTracker(standardBinding)
        val enhanced = mockk<Komga> { every { isLoggedIn } returns true }
        every { trackerManager.get(6) } returns enhanced
        coEvery { getTracks.await(1) } returns listOf(standardBinding, binding(6))
        val remote = standardBinding.copy(lastChapterRead = 2.5)
        coEvery { standard.fetchTrackProgress(any()) } returns remote.toDbTrack()
        val chapter = Chapter.create()
        coEvery { getChapters.await(1, false) } returns listOf(
            chapter.copy(id = 1, chapterNumber = 1.0, lastPageRead = 7, bookmark = true),
            chapter.copy(id = 2, chapterNumber = 2.5),
            chapter.copy(id = 3, chapterNumber = 3.0),
            chapter.copy(id = 4, chapterNumber = 4.0, read = true),
        )
        val updates = slot<List<ChapterUpdate>>()
        coEvery { updateChapter.awaitAll(capture(updates)) } returns Unit

        assertTrue(refresh.await(1, standardProgressOnly = true).isEmpty())

        assertEquals(listOf(1L, 2L), updates.captured.map { it.id })
        updates.captured.forEach {
            assertEquals(true, it.read)
            assertNull(it.lastPageRead)
            assertNull(it.bookmark)
        }
        coVerify(exactly = 1) { insertTrack.await(remote) }
        coVerify(exactly = 0) { standard.refresh(any<DbMangaTrack>()) }
        coVerify(exactly = 0) { standard.update(any<DbMangaTrack>(), any()) }
        coVerify(exactly = 0) { enhanced.refresh(any()) }
    }

    @Test
    fun `a missing remote entry does not use legacy refresh to recreate it`() = runTest {
        preferences.importTrackerProgress.set(true)
        val binding = binding(1)
        val tracker = standardTracker(binding)
        coEvery { getTracks.await(1) } returns listOf(binding)
        coEvery { tracker.fetchTrackProgress(any()) } returns null

        assertTrue(refresh.await(1, standardProgressOnly = true).isEmpty())

        coVerify(exactly = 0) { tracker.refresh(any<DbMangaTrack>()) }
        coVerify(exactly = 0) { insertTrack.await(any()) }
        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
    }

    @Test
    fun `the tracker sheet still refreshes bindings when local progress import is disabled`() = runTest {
        val binding = binding(1)
        val tracker = standardTracker(binding)
        val remote = binding.copy(lastChapterRead = 1.0)
        coEvery { getTracks.await(1) } returns listOf(binding)
        coEvery { tracker.refresh(any<DbMangaTrack>()) } returns remote.toDbTrack()

        assertTrue(refresh.await(1).isEmpty())

        coVerify(exactly = 1) { insertTrack.await(remote) }
        coVerify(exactly = 0) { tracker.fetchTrackProgress(any()) }
        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
    }

    @Test
    fun `a remote failure does not prevent a different standard tracker from importing progress`() = runTest {
        preferences.importTrackerProgress.set(true)
        val failingBinding = binding(1)
        val workingBinding = binding(2)
        val failing = standardTracker(failingBinding)
        val working = standardTracker(workingBinding)
        val error = IOException("fixture unavailable")
        coEvery { getTracks.await(1) } returns listOf(failingBinding, workingBinding)
        coEvery { failing.fetchTrackProgress(any()) } throws error
        val remote = workingBinding.copy(lastChapterRead = 1.0)
        coEvery { working.fetchTrackProgress(any()) } returns remote.toDbTrack()
        coEvery { getChapters.await(1, false) } returns listOf(Chapter.create().copy(id = 1, chapterNumber = 1.0))
        coEvery { updateChapter.awaitAll(any()) } returns Unit

        assertEquals(listOf(failing to error), refresh.await(1, standardProgressOnly = true))

        coVerify(exactly = 1) { insertTrack.await(remote) }
        coVerify(exactly = 1) { updateChapter.awaitAll(any()) }
    }

    @Test
    fun `a cancelled remote refresh propagates cancellation without writing progress`() = runTest {
        preferences.importTrackerProgress.set(true)
        val binding = binding(1)
        val tracker = standardTracker(binding)
        coEvery { getTracks.await(1) } returns listOf(binding)
        coEvery { tracker.fetchTrackProgress(any()) } throws CancellationException("fixture cancelled")

        assertThrows<CancellationException> { refresh.await(1, standardProgressOnly = true) }

        coVerify(exactly = 0) { insertTrack.await(any()) }
        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
    }

    private fun standardTracker(binding: MangaTrack): MyAnimeList {
        val tracker = mockk<MyAnimeList>()
        every { tracker.isLoggedIn } returns true
        every { tracker.mangaService } returns tracker
        every { trackerManager.get(binding.trackerId) } returns tracker
        return tracker
    }

    private fun binding(trackerId: Long) = MangaTrack(
        id = trackerId,
        mangaId = 1,
        trackerId = trackerId,
        remoteId = 10,
        libraryId = null,
        title = "Synthetic manga",
        lastChapterRead = 0.0,
        totalChapters = 4,
        status = 1,
        score = 0.0,
        remoteUrl = "",
        startDate = 0,
        finishDate = 0,
        private = false,
    )
}
