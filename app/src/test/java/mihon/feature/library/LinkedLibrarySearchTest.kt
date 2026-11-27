package mihon.feature.library

import eu.kanade.tachiyomi.ui.library.LibrarySearchAlias
import eu.kanade.tachiyomi.ui.library.LinkedLibraryRow
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import mihon.domain.library.model.search.AndNode
import mihon.domain.library.model.search.Comparator
import mihon.domain.library.model.search.ComparisonField
import mihon.domain.library.model.search.ComparisonQueryNode
import mihon.domain.library.model.search.FieldQueryNode
import mihon.domain.library.model.search.GeneralQueryNode
import mihon.domain.library.model.search.MangaField
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.merge.interactor.MergedEntry
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga

class LinkedLibrarySearchTest {
    @Test
    fun `linked title search uses source aliases and projected unread counts`() {
        val first = MergeEntryRef(1L, "/favorite")
        val second = MergeEntryRef(2L, "/metadata")
        val favorite = Manga.create().copy(id = 17L, source = first.sourceId, url = first.url, ogTitle = "Search alias")
        val metadata = Manga.create().copy(
            id = 23L,
            source = second.sourceId,
            url = second.url,
            ogTitle = "Chosen title",
        )
        val link = LinkedLibraryRow<Manga, Chapter>(
            group = EntryMerge("group", MergeMedia.Manga, second, listOf(first, second)),
            snapshot = MergedEntry(metadata, emptyList(), emptyList(), mapOf(first to favorite, second to metadata)),
            favoriteMembers = listOf(favorite),
            aliases = listOf(
                LibrarySearchAlias(favorite, "source one", "en"),
                LibrarySearchAlias(metadata, "source two", "it"),
            ),
        )
        val item = MangaLibraryItem(
            libraryManga = LibraryManga(favorite, 0L, 3L, 1L, 0L, 0L, 0L, 0L),
            downloadCount = 0,
            unreadCount = 2,
            isLocal = false,
            sourceName = "source two",
            sourceLanguage = "it",
            badges = MangaLibraryItem.Badges(0, 0, false, ""),
            displayManga = metadata,
            linked = link,
        )

        assertTrue(GeneralQueryNode("Search alias", negated = false).matches(item))
        assertTrue(FieldQueryNode(MangaField.TITLE, "Chosen title", negated = false).matches(item))
        assertFalse(FieldQueryNode(MangaField.SOURCE, "source one", negated = true).matches(item))
        assertTrue(
            AndNode(
                listOf(
                    FieldQueryNode(MangaField.SOURCE, "source one", negated = false),
                    ComparisonQueryNode(ComparisonField.UNREAD, "2", Comparator.EQ, negated = false),
                ),
            ).matches(item),
        )
    }
}
