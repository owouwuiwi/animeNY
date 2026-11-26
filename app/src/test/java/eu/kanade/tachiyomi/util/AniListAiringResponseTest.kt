package eu.kanade.tachiyomi.util

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AniListAiringResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads formatted schedules regardless of field order`() {
        val body = """
            {
                "data": {
                    "Media": {
                        "nextAiringEpisode": { "airingAt": 1795800000, "episode": 9, "id": 42 }
                    }
                }
            }
        """.trimIndent()

        assertEquals(9 to 1795800000L, json.parseAniListAiringEpisode(body))
    }

    @Test
    fun `explicitly missing next episode clears an old schedule`() {
        val body = """{"data":{"Media":{"nextAiringEpisode":null}}}"""

        assertEquals(NO_UPCOMING_EPISODE, json.parseAniListAiringEpisode(body))
    }

    @Test
    fun `failed lookups do not replace an old schedule`() {
        assertNull(json.parseAniListAiringEpisode("""{"data":null,"errors":[{"message":"Error"}]}"""))
        assertNull(json.parseAniListAiringEpisode("not JSON"))
    }
}
