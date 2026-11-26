package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.compose.LazyPagingItems

/**
 * Keeps a composition attached to its entry even when lists insert or move items.
 * A catalogue can repeat an entry, so distinct occurrences still need distinct keys.
 */
internal fun <T> List<T>.stableLazyKeys(identity: (T) -> String): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return map { item ->
        val key = identity(item)
        val occurrence = occurrences.getOrDefault(key, 0)
        occurrences[key] = occurrence + 1
        "item:$key:$occurrence"
    }
}

@Composable
internal fun <T : Any> LazyPagingItems<T>.rememberStablePagingKeys(identity: (T) -> String): (Int) -> String {
    val snapshot = itemSnapshotList
    val itemKeys = remember(snapshot, identity) { snapshot.items.stableLazyKeys(identity) }
    return { index ->
        itemKeys.getOrNull(index - snapshot.placeholdersBefore) ?: "placeholder:$index"
    }
}
