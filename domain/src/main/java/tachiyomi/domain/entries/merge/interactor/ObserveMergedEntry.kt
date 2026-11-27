package tachiyomi.domain.entries.merge.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.domain.entries.merge.model.MergedPart
import tachiyomi.domain.entries.merge.service.projectMergedParts
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.items.episode.service.episodeTrackingNumbers

@Inject
class ObserveMergedEntry(
    private val getAnime: GetAnime,
    private val getManga: GetManga,
    private val episodeRepository: EpisodeRepository,
    private val chapterRepository: ChapterRepository,
) {
    fun anime(merge: EntryMerge): Flow<MergedEntry<Anime, Episode>> {
        require(merge.media == MergeMedia.Anime)
        val members = merge.members.map(::observeAnime)
        return combine(members) { resolved -> project(merge, resolved.filterNotNull()) }
    }

    fun manga(merge: EntryMerge): Flow<MergedEntry<Manga, Chapter>> {
        require(merge.media == MergeMedia.Manga)
        val members = merge.members.map(::observeManga)
        return combine(members) { resolved -> project(merge, resolved.filterNotNull()) }
    }

    private fun observeAnime(reference: MergeEntryRef): Flow<ResolvedMember<Anime, Episode>?> {
        return getAnime.subscribe(reference.url, reference.sourceId).flatMapLatest { anime ->
            if (anime == null) return@flatMapLatest flowOf(null)
            episodeRepository.getEpisodeByAnimeIdAsFlow(anime.id).map { episodes ->
                val numbers = episodeTrackingNumbers(anime, episodes)
                ResolvedMember(
                    reference,
                    anime,
                    episodes
                        .filter { it.animeId == anime.id }
                        .map { MergedPart(reference, it.animeId, it.id, numbers[it.id], it.name, it) },
                )
            }
        }
    }

    private fun observeManga(reference: MergeEntryRef): Flow<ResolvedMember<Manga, Chapter>?> {
        return getManga.subscribe(reference.url, reference.sourceId).flatMapLatest { manga ->
            if (manga == null) return@flatMapLatest flowOf(null)
            chapterRepository.getChapterByMangaIdAsFlow(manga.id, applyScanlatorFilter = true).map { chapters ->
                ResolvedMember(
                    reference,
                    manga,
                    chapters
                        .filter { it.mangaId == manga.id }
                        .map { MergedPart(reference, it.mangaId, it.id, it.chapterNumber, it.name, it) },
                )
            }
        }
    }

    private fun <E, T> project(merge: EntryMerge, members: List<ResolvedMember<E, T>>): MergedEntry<E, T> {
        val resolved = members.associateBy { it.reference }
        val metadata = resolved[merge.metadata]?.entry
        val parts = projectMergedParts(merge, resolved.mapValues { it.value.parts })
        return MergedEntry(metadata, parts, merge.members.filterNot(resolved::containsKey))
    }

    private data class ResolvedMember<E, T>(
        val reference: MergeEntryRef,
        val entry: E,
        val parts: List<MergedPart<T>>,
    )
}

data class MergedEntry<E, T>(
    val metadata: E?,
    val parts: List<MergedPart<T>>,
    val unavailableMembers: List<MergeEntryRef>,
)
