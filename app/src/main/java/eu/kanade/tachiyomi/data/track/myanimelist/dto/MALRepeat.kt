package eu.kanade.tachiyomi.data.track.myanimelist.dto

import eu.kanade.domain.track.repeat.RemoteRepeat
import eu.kanade.domain.track.repeat.RepeatKind
import eu.kanade.domain.track.repeat.RepeatStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MALRepeatItem(
    @SerialName("my_list_status")
    val myListStatus: MALRepeatStatus? = null,
)

@Serializable
internal data class MALRepeatStatus(
    val status: String? = null,
    @SerialName("is_rewatching")
    val isRewatching: Boolean = false,
    @SerialName("is_rereading")
    val isRereading: Boolean = false,
    @SerialName("num_times_rewatched")
    val rewatched: Int? = null,
    @SerialName("num_times_reread")
    val reread: Int? = null,
) {
    fun toRemoteRepeat(kind: RepeatKind, id: Long): RemoteRepeat {
        val repeating = if (kind == RepeatKind.Anime) isRewatching else isRereading
        val repeatStatus = when {
            repeating -> RepeatStatus.Repeating
            status == "completed" -> RepeatStatus.Completed
            else -> RepeatStatus.Other
        }
        return RemoteRepeat(id, repeatStatus, if (kind == RepeatKind.Anime) rewatched else reread)
    }
}
