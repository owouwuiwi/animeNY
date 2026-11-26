package eu.kanade.domain.source.interactor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class FilterSourcesByNameTest {
    @Test
    fun `searches source names in their original order`() {
        val sources = listOf("Read Local", "Catalogue Manga", "Catalog Reader", "Local Files")

        assertEquals(listOf("Catalogue Manga", "Catalog Reader"), sources.filterSourcesByName(" CATALOG ") { it })
    }

    @Test
    fun `clearing search restores the entire listing`() {
        val sources = listOf("Local Files", "Other Source")

        assertSame(sources, sources.filterSourcesByName(" ") { it })
        assertSame(sources, sources.filterSourcesByName(null) { it })
    }
}
