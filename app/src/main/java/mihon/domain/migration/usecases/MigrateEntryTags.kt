package mihon.domain.migration.usecases

import dev.zacsweers.metro.Inject
import tachiyomi.domain.entries.anime.interactor.GetCustomAnimeInfo
import tachiyomi.domain.entries.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.CustomAnimeInfo
import tachiyomi.domain.entries.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.entries.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.entries.manga.model.CustomMangaInfo
import tachiyomi.domain.entries.manga.model.Manga

@Inject
class MigrateEntryTags(
    private val getCustomAnimeInfo: GetCustomAnimeInfo,
    private val setCustomAnimeInfo: SetCustomAnimeInfo,
    private val getCustomMangaInfo: GetCustomMangaInfo,
    private val setCustomMangaInfo: SetCustomMangaInfo,
) {
    fun copy(from: Anime, to: Anime) {
        val fromTags = getCustomAnimeInfo.get(from.id)?.genre ?: from.ogGenre
        val toInfo = getCustomAnimeInfo.get(to.id)
        val tags = mergeMigrationTags(fromTags, toInfo?.genre ?: to.ogGenre) ?: return

        setCustomAnimeInfo.set(toInfo?.copy(genre = tags) ?: CustomAnimeInfo(to.id, title = null, genre = tags))
    }

    fun copy(from: Manga, to: Manga) {
        val fromTags = getCustomMangaInfo.get(from.id)?.genre ?: from.ogGenre
        val toInfo = getCustomMangaInfo.get(to.id)
        val tags = mergeMigrationTags(fromTags, toInfo?.genre ?: to.ogGenre) ?: return

        setCustomMangaInfo.set(toInfo?.copy(genre = tags) ?: CustomMangaInfo(to.id, title = null, genre = tags))
    }
}
