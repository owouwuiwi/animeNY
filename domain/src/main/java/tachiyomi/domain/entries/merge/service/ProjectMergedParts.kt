package tachiyomi.domain.entries.merge.service

import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeDuplicates
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergedPart

private val multipleSpaces = Regex("\\s+")

fun <T> projectMergedParts(
    merge: EntryMerge,
    partsByMember: Map<MergeEntryRef, List<MergedPart<T>>>,
): List<MergedPart<T>> {
    val origins = HashSet<Pair<Long, Long>>()
    val numbers = HashSet<Double>()
    val titles = HashSet<String>()
    return merge.members.asSequence()
        .flatMap { member -> partsByMember[member].orEmpty().asSequence().filter { it.member == member } }
        .filter { part -> origins.add(part.ownerId to part.partId) }
        .filter { part ->
            when (merge.duplicates) {
                MergeDuplicates.KeepAll -> true

                MergeDuplicates.Number -> part.validNumber()?.let(numbers::add) ?: true

                MergeDuplicates.Title -> part.title.trim().replace(multipleSpaces, " ").lowercase()
                    .takeIf(String::isNotBlank)?.let(titles::add) ?: true
            }
        }
        // Stable sorting keeps group priority and source order when the numbers match or are unknown.
        .sortedBy { it.validNumber() ?: Double.POSITIVE_INFINITY }
        .toList()
}

private fun MergedPart<*>.validNumber(): Double? {
    return number?.takeIf { it.isFinite() && it >= 0.0 }?.let { it + 0.0 }
}
