package tachiyomi.domain.library.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LibraryTagGroupTest {
    @Test
    fun `small libraries never lose entries when grouped by tag`() {
        val entries = listOf(
            Entry(1, null),
            Entry(2, emptyList()),
            Entry(3, listOf(" ", "Rare")),
            Entry(4, listOf("Rare", "Rare", "Rare", "Rare")),
        )

        assertEquals(
            listOf(LibraryTagGroup(null, entries)),
            groupLibraryEntriesByTag(entries, Entry::tags),
        )
    }

    @Test
    fun `rare tags and missing tags share one section without hiding entries`() {
        val shared = (1..4).map { Entry(it, listOf("Drama", "Adventure")) }
        val rare = Entry(5, listOf("Comedy"))
        val missing = Entry(6, emptyList())
        val groups = groupLibraryEntriesByTag(shared + listOf(rare, missing), Entry::tags)

        assertEquals(listOf("Drama", "Adventure", null), groups.map { it.tag })
        assertEquals(shared, groups[0].entries)
        assertEquals(listOf(rare, missing), groups.last().entries)
        assertNotEquals(groups[0].id, groups.last().id)
    }

    private data class Entry(val id: Int, val tags: List<String>?)
}
