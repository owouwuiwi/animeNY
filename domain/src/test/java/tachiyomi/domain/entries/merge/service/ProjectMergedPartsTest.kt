package tachiyomi.domain.entries.merge.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.merge.model.EntryMerge
import tachiyomi.domain.entries.merge.model.MergeDuplicates
import tachiyomi.domain.entries.merge.model.MergeEntryRef
import tachiyomi.domain.entries.merge.model.MergeMedia
import tachiyomi.domain.entries.merge.model.MergedPart

class ProjectMergedPartsTest {
    private val metadata = MergeEntryRef(10, "/original")
    private val preferred = MergeEntryRef(20, "/alternate")

    @Test
    fun `number dedupe keeps the selected source and the actual part and owner IDs`() {
        val local = part(metadata, owner = 200, id = 110, number = 1.0)
        val other = part(preferred, owner = 300, id = 120, number = 1.0)
        val next = part(metadata, owner = 200, id = 111, number = 2.0)

        assertEquals(
            listOf(other, next),
            projectMergedParts(
                merge(MergeDuplicates.Number),
                mapOf(
                    metadata to listOf(local, next),
                    preferred to listOf(other),
                ),
            ),
        )
    }

    @Test
    fun `title matching is independent of number and tolerates casing and spacing`() {
        val highPriority = part(preferred, 300, 120, null, " The   Journey ")
        val lowerPriority = part(metadata, 200, 110, 2.0, "the journey")
        val blank = part(metadata, 200, 111, 3.0, "")
        val secondBlank = part(preferred, 300, 121, 4.0, "")

        assertEquals(
            listOf(blank, secondBlank, highPriority),
            projectMergedParts(
                merge(MergeDuplicates.Title),
                mapOf(metadata to listOf(lowerPriority, blank), preferred to listOf(highPriority, secondBlank)),
            ),
        )
    }

    @Test
    fun `keep all still drops repeated database rows and excludes unavailable or unrelated members`() {
        val selected = part(preferred, 300, 120, 1.5)
        val alternate = part(metadata, 200, 110, 1.5)
        val unrelated = part(MergeEntryRef(30, "/different"), 400, 130, 1.0)
        val unavailable =
            projectMergedParts(merge(MergeDuplicates.KeepAll), mapOf(preferred to listOf(selected, unrelated)))
        val withDuplicate = projectMergedParts(
            merge(MergeDuplicates.KeepAll),
            mapOf(metadata to listOf(alternate), preferred to listOf(selected, selected)),
        )

        assertEquals(listOf(selected), unavailable)
        assertEquals(listOf(selected, alternate), withDuplicate)
    }

    @Test
    fun `unknown numbers cannot suppress real alternatives or a second unknown number`() {
        val numbered = part(metadata, 200, 110, 0.0)
        val zero = part(preferred, 300, 120, -0.0)
        val unknown = part(metadata, 200, 111, -1.0)
        val nonFinite = part(preferred, 300, 121, Double.NaN)
        assertEquals(
            listOf(zero, nonFinite, unknown),
            projectMergedParts(
                merge(MergeDuplicates.Number),
                mapOf(metadata to listOf(numbered, unknown), preferred to listOf(zero, nonFinite)),
            ),
        )
    }

    @Test
    fun `metadata selection must be a member and independent from source priority`() {
        assertEquals(metadata, merge(MergeDuplicates.KeepAll).metadata)
        assertThrows(IllegalArgumentException::class.java) {
            EntryMerge("test", MergeMedia.Manga, metadata, listOf(preferred, preferred))
        }
    }

    private fun merge(duplicates: MergeDuplicates) =
        EntryMerge("test", MergeMedia.Manga, metadata, listOf(preferred, metadata), duplicates)

    private fun part(
        member: MergeEntryRef,
        owner: Long,
        id: Long,
        number: Double?,
        title: String = "Part $id",
    ) = MergedPart(member, owner, id, number, title, Unit)
}
