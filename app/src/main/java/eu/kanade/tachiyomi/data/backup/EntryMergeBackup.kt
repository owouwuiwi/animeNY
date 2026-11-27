package eu.kanade.tachiyomi.data.backup

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupEntryMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.domain.entries.merge.repository.EntryMergeRepository

@Inject
class EntryMergeBackup(
    private val groups: EntryMergeRepository,
    private val getAnime: GetAnimeByUrlAndSourceId,
    private val getManga: GetMangaByUrlAndSourceId,
) {
    suspend fun snapshot(): List<EntryMerge> {
        return try {
            groups.observeAll().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not read merged entries for backup" }
            emptyList()
        }
    }

    /** Apply after library entries have been restored and their local IDs are available. */
    suspend fun restore(item: BackupEntryMerge): Boolean {
        val merge = item.toDomain() ?: return false
        for (member in merge.members) {
            val exists = when (merge.media) {
                MergeMedia.Anime -> getAnime.await(member.url, member.sourceId) != null
                MergeMedia.Manga -> getManga.await(member.url, member.sourceId) != null
            }
            if (!exists) return false
        }
        return groups.save(merge, replaceExisting = false)
    }
}

internal suspend fun <T : Any> includeLinkedBackupEntries(
    entries: List<T>,
    groups: List<EntryMerge>,
    reference: (T) -> MergeEntryRef?,
    resolve: suspend (MergeEntryRef) -> T?,
): List<T> {
    if (groups.isEmpty()) return entries
    val exported = entries.mapNotNullTo(hashSetOf(), reference)
    val links = groups.flatMap { group ->
        // A link may bring along its sources only when the export already includes part of that title.
        if (group.members.none(exported::contains)) return@flatMap emptyList()
        group.members.filterNot(exported::contains).map { resolve(it) ?: return@flatMap emptyList() }
    }
    return entries + links
}
