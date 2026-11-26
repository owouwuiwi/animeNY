package eu.kanade.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StableLazyKeysTest {
    @Test
    fun `entries keep their keys when the list is inserted into or reordered`() {
        val original = listOf("anime-a", "anime-b").stableLazyKeys { it }
        val updated = listOf("other", "anime-b", "anime-a").stableLazyKeys { it }

        assertEquals(original[0], updated[2])
        assertEquals(original[1], updated[1])
    }

    @Test
    fun `repeated catalogue entries have distinct keys`() {
        val keys = listOf("same", "same", "same:0", "same").stableLazyKeys { it }

        assertEquals(keys.size, keys.toSet().size)
    }
}
