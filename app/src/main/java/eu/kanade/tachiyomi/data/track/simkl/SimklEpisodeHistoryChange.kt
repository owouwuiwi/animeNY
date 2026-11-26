package eu.kanade.tachiyomi.data.track.simkl

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal data class SimklEpisodeHistoryChange(val watched: IntRange, val unwatched: IntRange) {
    companion object {
        fun fromProgress(progress: Double, previousEditedProgress: Double?): SimklEpisodeHistoryChange {
            val current = progress.toInt().coerceAtLeast(0)
            val previous = previousEditedProgress?.toInt() ?: current
            val unwatched = if (previous > current) current + 1..previous else IntRange.EMPTY
            return SimklEpisodeHistoryChange(1..current, unwatched)
        }
    }
}

internal fun simklEpisodeHistoryPayload(simklId: Long, episodes: IntRange) = buildJsonObject {
    require(!episodes.isEmpty())
    putJsonArray("shows") {
        addJsonObject {
            putJsonObject("ids") {
                put("simkl", simklId)
            }
            putJsonArray("seasons") {
                addJsonObject {
                    put("number", 1)
                    putJsonArray("episodes") {
                        for (episode in episodes) {
                            addJsonObject { put("number", episode) }
                        }
                    }
                }
            }
        }
    }
}
