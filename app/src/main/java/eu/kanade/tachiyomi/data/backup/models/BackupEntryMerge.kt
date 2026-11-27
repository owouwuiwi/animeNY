package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeDuplicates
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia

@Serializable
data class BackupEntryMerge(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val media: Int = 0,
    @ProtoNumber(3) val metadata: BackupMergeMember? = null,
    @ProtoNumber(4) val members: List<BackupMergeMember> = emptyList(),
    @ProtoNumber(5) val duplicates: Int = 0,
) {
    internal fun toDomain(): EntryMerge? {
        val kind = when (media) {
            ANIME -> MergeMedia.Anime
            MANGA -> MergeMedia.Manga
            else -> return null
        }
        val duplicateRule = when (duplicates) {
            KEEP_ALL -> MergeDuplicates.KeepAll
            BY_NUMBER -> MergeDuplicates.Number
            BY_TITLE -> MergeDuplicates.Title
            else -> return null
        }
        val metadataEntry = metadata?.toDomain() ?: return null
        val references = members.map { it.toDomain() ?: return null }
        if (id.isBlank() || references.distinct().size != references.size ||
            references.size < 2 || metadataEntry !in references
        ) {
            return null
        }
        return EntryMerge(id, kind, metadataEntry, references, duplicateRule)
    }

    internal companion object {
        private const val ANIME = 1
        private const val MANGA = 2
        private const val KEEP_ALL = 0
        private const val BY_NUMBER = 1
        private const val BY_TITLE = 2

        fun from(merge: EntryMerge): BackupEntryMerge = BackupEntryMerge(
            id = merge.id,
            media = when (merge.media) {
                MergeMedia.Anime -> ANIME
                MergeMedia.Manga -> MANGA
            },
            metadata = BackupMergeMember.from(merge.metadata),
            members = merge.members.map(BackupMergeMember::from),
            duplicates = when (merge.duplicates) {
                MergeDuplicates.KeepAll -> KEEP_ALL
                MergeDuplicates.Number -> BY_NUMBER
                MergeDuplicates.Title -> BY_TITLE
            },
        )
    }
}

@Serializable
data class BackupMergeMember(
    @ProtoNumber(1) val source: Long = 0L,
    @ProtoNumber(2) val url: String = "",
) {
    internal fun toDomain(): MergeEntryRef? = url.takeIf(String::isNotBlank)?.let { MergeEntryRef(source, it) }

    internal companion object {
        fun from(reference: MergeEntryRef) = BackupMergeMember(reference.sourceId, reference.url)
    }
}

internal fun backupEntryMerges(
    groups: List<EntryMerge>,
    manga: Set<MergeEntryRef>,
    anime: Set<MergeEntryRef>,
): List<BackupEntryMerge> {
    return groups.mapNotNull { group ->
        val exportedEntries = when (group.media) {
            MergeMedia.Anime -> anime
            MergeMedia.Manga -> manga
        }
        group.takeIf { it.members.all(exportedEntries::contains) }?.let(BackupEntryMerge::from)
    }
}
