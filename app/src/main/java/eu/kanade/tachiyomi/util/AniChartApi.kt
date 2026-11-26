package eu.kanade.tachiyomi.util

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.simkl.Simkl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.model.Anime
import java.io.IOException
import java.time.OffsetDateTime
import java.util.Calendar

@Inject
class AniChartApi(
    private val network: NetworkHelper,
    private val json: Json,
) {
    internal suspend fun loadAiringTime(
        anime: Anime,
        trackItems: List<AnimeTrackItem>,
        manualFetch: Boolean,
    ): Pair<Int, Long> {
        val cached = anime.nextEpisodeToAir to anime.nextEpisodeAiringAt
        if (anime.status == SAnime.COMPLETED.toLong() && !manualFetch) return cached

        return withIOContext {
            var queriedBinding = false
            var receivedSchedule = false
            for (item in trackItems) {
                val id = item.track?.remoteId?.takeIf { it > 0 } ?: continue
                val airing = when (item.tracker) {
                    is Anilist -> getAnilistAiringEpisodeData(id)
                    is MyAnimeList -> getAnilistAiringEpisodeData(id, byMalId = true)
                    is Simkl -> getSimklAiringEpisodeData(id)
                    else -> continue
                }
                queriedBinding = true
                if (airing == null) continue
                receivedSchedule = true
                if (airing.second > 0L) return@withIOContext airing
            }
            if (queriedBinding && !receivedSchedule) cached else NO_UPCOMING_EPISODE
        }
    }

    private suspend fun getAnilistAiringEpisodeData(id: Long, byMalId: Boolean = false): Pair<Int, Long>? {
        val idArgument = if (byMalId) "idMal" else "id"
        val query = """
            query {
                Media($idArgument: $id, type: ANIME) {
                    nextAiringEpisode {
                        episode
                        airingAt
                    }
                }
            }
        """.trimIndent()
        val request = POST(
            "https://graphql.anilist.co",
            body = buildJsonObject { put("query", query) }.toString().toRequestBody(jsonMime),
        )
        return fetchAiringBody(request)?.let { json.parseAniListAiringEpisode(it) }
    }

    private suspend fun getSimklAiringEpisodeData(id: Long): Pair<Int, Long>? {
        var episodeNumber = 1
        var airingAt = 0L
        return withIOContext {
            val calendarTypes = listOf("anime", "tv", "movie_release")
            calendarTypes.forEach {
                val body = fetchAiringBody(GET("https://data.simkl.in/calendar/$it.json")) ?: return@withIOContext null

                val data = removeAiredSimkl(body)

                val malId = data.substringAfter("\"simkl_id\":$id,", "").substringAfter(
                    "\"mal\":\"",
                ).substringBefore("\"").toLongOrNull() ?: 0L
                if (malId != 0L) {
                    getAnilistAiringEpisodeData(malId, byMalId = true)
                        ?.takeIf { it.second > 0L }
                        ?.let { return@withIOContext it }
                }

                val epNum = data.substringAfter("\"simkl_id\":$id,", "").substringBefore("\"}}").substringAfterLast(
                    "\"episode\":",
                )
                episodeNumber = epNum.substringBefore(",").toIntOrNull() ?: episodeNumber

                val date = data.substringBefore("\"simkl_id\":$id,", "").substringAfterLast(
                    "\"date\":\"",
                ).substringBefore("\"")
                airingAt = if (date.isNotBlank()) toUnixTimestamp(date) else airingAt

                if (airingAt != 0L) return@withIOContext Pair(episodeNumber, airingAt)
            }
            return@withIOContext Pair(episodeNumber, airingAt)
        }
    }

    private suspend fun fetchAiringBody(request: Request): String? = try {
        network.client.newCall(request).await().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    } catch (_: IOException) {
        null
    }

    private fun removeAiredSimkl(body: String): String {
        val currentTimeInMillis = Calendar.getInstance().timeInMillis
        val index = body.split("\"date\":\"").drop(1).indexOfFirst {
            val date = it.substringBefore("\"")
            val time = if (date.isNotBlank()) toUnixTimestamp(date) else 0L
            time.times(1000) > currentTimeInMillis
        }
        return if (index >= 0) body.substring(index) else ""
    }

    private fun toUnixTimestamp(dateFormat: String): Long {
        val offsetDateTime = OffsetDateTime.parse(dateFormat)
        val instant = offsetDateTime.toInstant()
        return instant.epochSecond
    }
}
