package tachiyomi.domain.entries.merge.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tachiyomi.domain.entries.merge.interactor.MergedEntry
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.domain.entries.merge.repository.EntryMergeRepository

fun <E, P> EntryMergeRepository.observeLibraryLinks(
    media: MergeMedia,
    observe: (EntryMerge) -> Flow<MergedEntry<E, P>>,
): Flow<Map<EntryMerge, MergedEntry<E, P>>> {
    return observeAll()
        .map { groups -> groups.filter { it.media == media } }
        .distinctUntilChanged()
        .flatMapLatest { groups ->
            if (groups.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(groups.map { group -> observe(group).map { group to it } }) { it.toMap() }
            }
        }
        .catch { emit(emptyMap()) }
}

/**
 * Replaces linked favorite rows once in every category containing a member. The representative
 * comes from the same favorite in every category, irrespective of which member put it there.
 */
suspend fun <C, T, E, P> projectLinkedLibrary(
    library: Map<C, List<T>>,
    links: Map<EntryMerge, MergedEntry<E, P>>,
    reference: (T) -> MergeEntryRef?,
    toLinkedItem: suspend (C, T, List<T>, EntryMerge, MergedEntry<E, P>) -> T,
): Map<C, List<T>> {
    if (links.isEmpty()) return library
    val favorites = library.values.asSequence().flatten()
        .mapNotNull { row -> reference(row)?.let { it to row } }
        .toMap()
    val linksByMember = links.keys.flatMap { group -> group.members.map { it to group } }.toMap()
    val linkedFavorites = links.keys.associateWith { group ->
        group.members.mapNotNull(favorites::get)
    }
    val representatives = links.keys.associateWith { group ->
        favorites[group.metadata] ?: linkedFavorites.getValue(group).firstOrNull()
    }
    return library.mapValues { (category, rows) ->
        val emittedGroups = HashSet<String>()
        rows.mapNotNull { row ->
            val member = reference(row) ?: return@mapNotNull row
            val group = linksByMember[member] ?: return@mapNotNull row
            if (!emittedGroups.add(group.id)) return@mapNotNull null
            val representative = representatives[group] ?: return@mapNotNull row
            toLinkedItem(category, representative, linkedFavorites.getValue(group), group, links.getValue(group))
        }
    }
}
