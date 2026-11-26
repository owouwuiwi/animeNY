package tachiyomi.domain.entries.merge.model

import kotlinx.serialization.Serializable

@Serializable
enum class MergeMedia { Anime, Manga }

/** Stable source identity. Local integer database IDs are resolved when the merge is opened. */
@Serializable
data class MergeEntryRef(val sourceId: Long, val url: String) : java.io.Serializable {
    init {
        require(url.isNotBlank())
    }
}

@Serializable
enum class MergeDuplicates { KeepAll, Number, Title }

@Serializable
data class EntryMerge(
    val id: String,
    val media: MergeMedia,
    val metadata: MergeEntryRef,
    val members: List<MergeEntryRef>,
    val duplicates: MergeDuplicates = MergeDuplicates.KeepAll,
) {
    init {
        require(id.isNotBlank())
        require(members.size >= 2 && members.toSet().size == members.size)
        require(metadata in members)
    }
}

data class MergedPart<T>(
    val member: MergeEntryRef,
    val ownerId: Long,
    val partId: Long,
    val number: Double?,
    val title: String,
    val value: T,
)
