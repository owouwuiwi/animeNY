package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaExternalTrackerResult
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MangaBakaExternalTrackerIdsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extracts supported service IDs from the series source map`() {
        val result = json.decodeFromString<MangaBakaExternalTrackerResult>(
            """
            {"data":{"id":2,"source":{
                "anilist":{"id":30705,"rating":8},
                "my_anime_list":{"id":705},
                "manga_updates":{"id":"7s905mh"},
                "kitsu":{"id":1576},
                "shikimori":{"id":705},
                "anime_planet":{"id":"tsukihime"}
            }}}
            """.trimIndent(),
        )
        val ids = requireNotNull(result.data.source)
        assertEquals(30705L, ids.aniList?.id)
        assertEquals(705L, ids.myAnimeList?.id)
        assertEquals(1576L, ids.kitsu?.id)
        assertEquals(705L, ids.shikimori?.id)
        assertEquals(16945653113L, ids.mangaUpdates?.decimalId())
    }

    @Test
    fun `leaves unknown mappings empty instead of inferring an ID`() {
        val result = json.decodeFromString<MangaBakaExternalTrackerResult>(
            """{"data":{"source":{"anilist":{"id":null},"manga_updates":{"id":"/invalid"}}}}""",
        )
        val ids = requireNotNull(result.data.source)
        assertNull(ids.aniList?.id)
        assertNull(ids.myAnimeList)
        assertNull(ids.mangaUpdates?.decimalId())
        assertNull(json.decodeFromString<MangaBakaExternalTrackerResult>("""{"data":{}}""").data.source)
    }
}
