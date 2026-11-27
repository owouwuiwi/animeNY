package tachiyomi.data.entries.merge

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.repository.EntryMergeRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EntryMergeRepositoryImpl(preferences: PreferenceStore, private val json: Json) : EntryMergeRepository {
    private val state = preferences.getString(Preference.appStateKey("entry_merges_v1"), "[]")
    private val writeLock = Mutex()

    override fun observeAll() = state.changes().map(::decode).distinctUntilChanged()

    override suspend fun save(merge: EntryMerge, replaceExisting: Boolean): Boolean = withIOContext {
        writeLock.withLock {
            val groups = decode(state.get())
            val prior = groups.firstOrNull { it.id == merge.id }
            if (prior == merge) return@withLock true
            if (prior != null && (!replaceExisting || prior.media != merge.media)) return@withLock false
            val conflict = groups.any { existing ->
                existing.id != merge.id && existing.media == merge.media && existing.members.any { it in merge.members }
            }
            if (conflict) return@withLock false
            val updated = groups.filterNot { it.id == merge.id } + merge
            state.set(json.encodeToString(updated))
            true
        }
    }

    override suspend fun remove(id: String) = withIOContext {
        writeLock.withLock {
            val groups = decode(state.get())
            val updated = groups.filterNot { it.id == id }
            if (groups.size != updated.size) state.set(json.encodeToString(updated))
        }
    }

    private fun decode(raw: String): List<EntryMerge> = json.decodeFromString(raw)
}
