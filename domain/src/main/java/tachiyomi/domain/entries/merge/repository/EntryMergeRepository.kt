package tachiyomi.domain.entries.merge.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.merge.model.EntryMerge

interface EntryMergeRepository {
    fun observeAll(): Flow<List<EntryMerge>>
    suspend fun save(merge: EntryMerge): Boolean
    suspend fun remove(id: String)
}
