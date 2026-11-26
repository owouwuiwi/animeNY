package tachiyomi.domain.library.model

data class LibraryTagGroup<T>(
    val tag: String?,
    val entries: List<T>,
) {
    val id: Long get() = tag?.hashCode()?.toLong() ?: Long.MIN_VALUE
}

/**
 * Match the existing minimum size for a tag tab. Every entry without a tab is included in
 * the ungrouped section. Repeated tags on an entry do not affect the minimum size.
 */
fun <T> groupLibraryEntriesByTag(entries: List<T>, tagsOf: (T) -> List<String>?): List<LibraryTagGroup<T>> {
    val entriesByTag = linkedMapOf<String, MutableList<T>>()
    for (entry in entries) {
        val tags = tagsOf(entry).orEmpty().filter { it.isNotBlank() }.distinct()
        for (tag in tags) {
            entriesByTag.getOrPut(tag) { mutableListOf() }.add(entry)
        }
    }

    val largeGroups = entriesByTag.filterValues { it.size >= MIN_TAG_GROUP_SIZE }
    val taggedEntries = largeGroups.values.flatten().toSet()
    val ungrouped = entries.filterNot(taggedEntries::contains)

    return buildList {
        largeGroups.forEach { (tag, items) -> add(LibraryTagGroup(tag, items)) }
        if (ungrouped.isNotEmpty()) add(LibraryTagGroup(null, ungrouped))
    }
}

private const val MIN_TAG_GROUP_SIZE = 4
