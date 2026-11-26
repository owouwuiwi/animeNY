package eu.kanade.domain.ui.model

import eu.kanade.tachiyomi.ui.home.HomeTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class NavStyleTest {
    @Test
    fun `anime moved to More stays out of the navigation bar and has a visible start fallback`() {
        val style = NavStyle.MOVE_ANIME_TO_MORE

        assertEquals(AnimeLibraryTab, style.moreTab)
        assertFalse(AnimeLibraryTab in style.tabs)
        assertEquals(MangaLibraryTab, style.resolveStartTab(AnimeLibraryTab, showHome = false))
        assertEquals(MangaLibraryTab, style.resolveStartTab(HomeTab, showHome = false))
        assertEquals(HomeTab, style.resolveStartTab(AnimeLibraryTab, showHome = true))
    }
}
