package tachiyomi.domain.entries.merge.service

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.merge.interactor.MergedEntry
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeDuplicates
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.domain.entries.merge.model.MergedPart

class ProjectLinkedLibraryTest {
    private val primary = MergeEntryRef(1, "/primary")
    private val alternate = MergeEntryRef(2, "/alternate")
    private val metadata = MergeEntryRef(3, "/metadata")
    private val unlinked = MergeEntryRef(4, "/unlinked")
    private val first = Row(primary, 101)
    private val second = Row(alternate, 202)
    private val outside = Row(unlinked, 404)
    private val group = EntryMerge(
        "linked",
        MergeMedia.Anime,
        metadata,
        listOf(alternate, primary, metadata),
        MergeDuplicates.Number,
    )
    private val finished = MergedPart(alternate, 202L, 12L, 1.0, "First episode", true)
    private val duplicate = MergedPart(primary, 101L, 11L, 1.0, "First episode", false)
    private val pending = MergedPart(primary, 101L, 13L, 2.0, "Second episode", false)
    private val snapshot = MergedEntry(
        metadata = "Selected title",
        parts = projectMergedParts(group, mapOf(primary to listOf(duplicate, pending), alternate to listOf(finished))),
        unavailableMembers = emptyList(),
        members = mapOf(primary to "One", alternate to "Two", metadata to "Selected title"),
    )

    @Test
    fun `linked titles use one favorite across all member categories and show only projected parts`() = runTest {
        val projected = projectLinkedLibrary(
            mapOf("First" to listOf(first, second, outside), "Second" to listOf(first), "Unused" to emptyList()),
            mapOf(group to snapshot),
            Row::reference,
        ) { _, representative, favorites, _, link ->
            representative.copy(
                metadata = link.metadata,
                visiblePartIds = link.parts.map { it.partId },
                progress = link.parts.count { it.value },
                selectedIds = favorites.map { it.id },
            )
        }
        val expected = second.copy(
            metadata = "Selected title",
            visiblePartIds = listOf(12L, 13L),
            progress = 1,
            selectedIds = listOf(202, 101),
        )

        assertEquals(listOf(expected, outside), projected.getValue("First"))
        assertEquals(listOf(expected), projected.getValue("Second"))
        assertEquals(emptyList<Row>(), projected.getValue("Unused"))
    }

    @Test
    fun `a linked title without a library member does not create a tile`() = runTest {
        val library = mapOf("My category" to listOf(outside))
        val projected = projectLinkedLibrary(library, mapOf(group to snapshot), Row::reference) { _, _, _, _, _ ->
            error("Unfavorited linked titles are not library entries")
        }

        assertEquals(library, projected)
    }

    private data class Row(
        val reference: MergeEntryRef,
        val id: Int,
        val metadata: String? = null,
        val visiblePartIds: List<Long> = emptyList(),
        val progress: Int = 0,
        val selectedIds: List<Int> = emptyList(),
    )
}
