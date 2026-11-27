package tachiyomi.domain.source.model

enum class FeedSortOrder {
    MANUAL,
    SOURCE_NAME,
    LAST_UPDATE,
    ;

    fun <T> order(items: List<T>, sourceName: (T) -> String, lastLocalUpdate: (T) -> Long): List<T> = when (this) {
        MANUAL -> items
        SOURCE_NAME -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, sourceName))
        LAST_UPDATE -> items.sortedByDescending(lastLocalUpdate)
    }
}
