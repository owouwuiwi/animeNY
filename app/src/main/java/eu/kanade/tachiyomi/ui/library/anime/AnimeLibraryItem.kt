package eu.kanade.tachiyomi.ui.library.anime

import eu.kanade.tachiyomi.ui.library.LibrarySearchAlias
import eu.kanade.tachiyomi.ui.library.LinkedLibraryRow
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime

class AnimeLibraryItem(
    val libraryAnime: LibraryAnime,
    val downloadCount: Int,
    val unseenCount: Long,
    val isLocal: Boolean,
    val sourceName: String,
    val sourceLanguage: String,
    val badges: Badges,
    val displayAnime: Anime = libraryAnime.anime,
    val linked: LinkedLibraryRow<Anime, Episode>? = null,
) {
    val anime: Anime get() = libraryAnime.anime
    val id: Long get() = libraryAnime.id
    internal val searchAliases: List<LibrarySearchAlias<Anime>> by lazy {
        linked?.aliases.orEmpty().ifEmpty { listOf(LibrarySearchAlias(anime, sourceName, sourceLanguage)) }
    }

    data class Badges(
        val downloadCount: Int,
        val unseenCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
    )
}
