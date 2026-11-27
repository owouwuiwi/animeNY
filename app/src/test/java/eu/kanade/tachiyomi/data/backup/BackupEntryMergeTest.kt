package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupEntryMerge
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import eu.kanade.tachiyomi.data.backup.models.backupEntryMerges
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeDuplicates
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia

@OptIn(ExperimentalSerializationApi::class)
class BackupEntryMergeTest {
    private val first = MergeEntryRef(0, "/local")
    private val second = MergeEntryRef(-11, "/some-path")
    private val group = EntryMerge("example", MergeMedia.Manga, second, listOf(first, second), MergeDuplicates.Title)
    private val category = BackupCategory("Sample", order = 2)
    private val previousMetadata = PreMergeMetadata(listOf(category), isLegacy = false)

    @Test
    fun `entry merge metadata survives the actual streaming backup format`() {
        val encoded = Buffer()
        val metadata = Backup(
            backupCategories = listOf(category),
            isLegacy = false,
            backupEntryMerges = listOf(BackupEntryMerge.from(group)),
        )
        BackupWriter(encoded, ProtoBuf).writeMetadata(metadata)
        val restored = BackupReader(encoded, ProtoBuf).readMetadata()

        assertEquals(listOf(category), restored.backupCategories)
        assertEquals(group, restored.backupEntryMerges.single().toDomain())
    }

    @Test
    fun `legacy and prior current format files produce no groups and keep other fields`() {
        val oldCurrent = ProtoBuf.encodeToByteArray(PreMergeMetadata.serializer(), previousMetadata)
        val oldLegacy = ProtoBuf.encodeToByteArray(
            LegacyBackup.serializer(),
            LegacyBackup(
                backupCategories = listOf(category),
                backupAnimeSources = listOf(BackupAnimeSource("Legacy fixture", 20L)),
            ),
        )
        listOf(oldCurrent, oldLegacy).forEach { payload ->
            val restored = BackupReader(Buffer().write(payload), ProtoBuf).readMetadata()
            assertEquals(listOf(category), restored.backupCategories)
            assertEquals(emptyList<BackupEntryMerge>(), restored.backupEntryMerges)
        }
    }

    @Test
    fun `a previous decoder can ignore the optional group field`() {
        val payload = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(
                backupCategories = listOf(category),
                isLegacy = false,
                backupEntryMerges = listOf(BackupEntryMerge.from(group)),
            ),
        )
        assertEquals(previousMetadata, ProtoBuf.decodeFromByteArray(PreMergeMetadata.serializer(), payload))
    }

    @Test
    fun `category filtered backup includes complete groups of the exported media type only`() {
        val anime = group.copy(id = "anime", media = MergeMedia.Anime)
        val groups = listOf(group, anime)

        assertEquals(emptyList<BackupEntryMerge>(), backupEntryMerges(groups, setOf(first), setOf(second)))
        val entries = backupEntryMerges(groups, setOf(first, second), emptySet())
        assertEquals(listOf(group), entries.map(BackupEntryMerge::toDomain))
    }

    @Test
    fun `a library export includes the other source of a linked title without favoriting it`() = runTest {
        val library = BackupManga(first.sourceId, first.url, favorite = true)
        val linked = BackupManga(second.sourceId, second.url, favorite = false)
        val outside = group.copy(
            id = "outside",
            metadata = MergeEntryRef(3, "/outside"),
            members = listOf(MergeEntryRef(3, "/outside"), MergeEntryRef(4, "/other")),
        )
        val groups = listOf(group, outside)
        val reference = { entry: BackupManga -> MergeEntryRef(entry.source, entry.url) }
        val selected = includeLinkedBackupEntries(listOf(library), groups, reference) { member ->
            assertEquals(second, member)
            linked
        }
        val exported = backupEntryMerges(groups, selected.map(reference).toSet(), emptySet())
        val encoded = Buffer()
        val writer = BackupWriter(encoded, ProtoBuf)
        selected.forEach(writer::writeMangaEntry)
        writer.writeMetadata(Backup(isLegacy = false, backupEntryMerges = exported))
        val reader = BackupReader(encoded, ProtoBuf)
        val restored = buildList {
            while (reader.nextEntry()) add(reader.readMangaEntry())
        }

        assertEquals(listOf(first to true, second to false), restored.map { reference(it) to it.favorite })
        assertEquals(listOf(group), reader.readMetadata().backupEntryMerges.map(BackupEntryMerge::toDomain))
    }

    @Test
    fun `a missing linked source does not pull other dependencies into the export`() = runTest {
        val missing = MergeEntryRef(3, "/missing")
        val partial = group.copy(members = listOf(first, second, missing))
        val selected = includeLinkedBackupEntries(listOf(first), listOf(partial), { it }) {
            it.takeUnless { member -> member == missing }
        }

        assertEquals(listOf(first), selected)
    }

    @Test
    fun `malformed relationships are skipped without changing the other metadata`() {
        val wire = BackupEntryMerge.from(group)
        assertNull(wire.copy(media = 42).toDomain())
        assertNull(wire.copy(duplicates = 42).toDomain())
        assertNull(wire.copy(metadata = null).toDomain())
        assertNull(wire.copy(members = listOf(wire.members.first(), wire.members.first())).toDomain())
    }

    @Serializable
    private data class PreMergeMetadata(
        @ProtoNumber(2) val categories: List<BackupCategory> = emptyList(),
        @ProtoNumber(500) val isLegacy: Boolean = true,
    )
}
