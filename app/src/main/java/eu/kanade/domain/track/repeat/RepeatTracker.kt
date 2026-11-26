package eu.kanade.domain.track.repeat

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatKind { Anime, Manga }

enum class RepeatStatus { Completed, Repeating, Other }

data class RemoteRepeat(
    val entryId: Long,
    val status: RepeatStatus,
    val count: Int?,
)

/** Trackers that can read and set an absolute repeat count without changing ordinary progress. */
interface RepeatTracker {
    val maxRepeatCount: Int get() = Int.MAX_VALUE

    suspend fun getRepeat(kind: RepeatKind, remoteId: Long): RemoteRepeat?
    suspend fun updateRepeat(kind: RepeatKind, entry: RemoteRepeat, status: RepeatStatus, count: Int?): RemoteRepeat
}
