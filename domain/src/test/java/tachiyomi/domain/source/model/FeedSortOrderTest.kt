package tachiyomi.domain.source.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeedSortOrderTest {
    private val rows = listOf(
        Row(1, "zeta", 0),
        Row(2, "Alpha", 300),
        Row(3, "alpha", 100),
        Row(4, "Beta", 300),
        Row(5, "Anonymous", 0),
    )

    @Test
    fun `manual order preserves the saved order`() {
        assertEquals(listOf(1, 2, 3, 4, 5), orderedIds(FeedSortOrder.MANUAL))
    }

    @Test
    fun `source name ignores case and keeps equal names in saved order`() {
        assertEquals(listOf(2, 3, 5, 4, 1), orderedIds(FeedSortOrder.SOURCE_NAME))
    }

    @Test
    fun `latest local update is descending with unknown dates last`() {
        assertEquals(listOf(2, 4, 3, 1, 5), orderedIds(FeedSortOrder.LAST_UPDATE))
    }

    private fun orderedIds(order: FeedSortOrder): List<Int> =
        order.order(rows, Row::name, Row::lastLocalUpdate).map(Row::id)

    private data class Row(val id: Int, val name: String, val lastLocalUpdate: Long)
}
