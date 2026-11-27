package eu.kanade.tachiyomi.ui.library.manga

import eu.kanade.tachiyomi.ui.library.LibrarySearchAlias
import eu.kanade.tachiyomi.ui.library.LinkedLibraryRow
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga

class MangaLibraryItem(
    val libraryManga: LibraryManga,
    val downloadCount: Int,
    val unreadCount: Long,
    val isLocal: Boolean,
    val sourceName: String,
    val sourceLanguage: String,
    val badges: Badges,
    val displayManga: Manga = libraryManga.manga,
    val linked: LinkedLibraryRow<Manga, Chapter>? = null,
) {
    val manga: Manga get() = libraryManga.manga
    val id: Long get() = libraryManga.id
    internal val searchAliases: List<LibrarySearchAlias<Manga>> by lazy {
        linked?.aliases.orEmpty().ifEmpty { listOf(LibrarySearchAlias(manga, sourceName, sourceLanguage)) }
    }

    data class Badges(
        val downloadCount: Int,
        val unreadCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
    )
}
