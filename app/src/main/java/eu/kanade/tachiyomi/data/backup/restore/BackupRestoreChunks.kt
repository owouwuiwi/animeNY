package eu.kanade.tachiyomi.data.backup.restore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Limits the combined cover bytes kept for a database batch. An oversized entry is handled on its own. */
internal fun <T> Flow<T>.chunkBySize(
    maxItems: Int,
    maxBytes: Long,
    sizeOf: (T) -> Long,
): Flow<List<T>> = flow {
    var chunk = ArrayList<T>(maxItems)
    var chunkBytes = 0L
    collect { entry ->
        val entryBytes = sizeOf(entry)
        if (chunk.isNotEmpty() && (chunk.size == maxItems || chunkBytes + entryBytes > maxBytes)) {
            emit(chunk)
            chunk = ArrayList(maxItems)
            chunkBytes = 0L
        }
        chunk.add(entry)
        chunkBytes += entryBytes
    }
    if (chunk.isNotEmpty()) emit(chunk)
}
