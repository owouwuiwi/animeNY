package eu.kanade.domain.track.repeat

import kotlinx.serialization.Serializable

@Serializable
internal data class RepeatKey(
    val kind: RepeatKind,
    val id: Long,
    val source: Long,
    val url: String,
)

@Serializable
internal data class RepeatTarget(
    val trackerId: Long,
    val trackId: Long,
    val remoteId: Long,
    val account: String,
    val total: Int,
    val targetCount: Int? = null,
    val startConfirmed: Boolean = false,
    val finished: Boolean = false,
)

@Serializable
internal data class RepeatSession(
    val id: String,
    val key: RepeatKey,
    val targets: List<RepeatTarget>,
    val started: Boolean = false,
    val completed: Set<Int> = emptySet(),
) {
    val total: Int
        get() = targets.maxOf { it.total }

    fun record(number: Int, partCompleted: Boolean): RepeatSession {
        if (number !in 1..total) return this
        if (!started && number != 1) return this
        return copy(
            started = true,
            completed = if (partCompleted) completed + number else completed,
        )
    }

    fun isComplete(total: Int): Boolean = completed.size >= total && (1..total).all(completed::contains)

    fun nextPart(): Int? = (1..total).firstOrNull { it !in completed }

    fun requiresSync(): Boolean = started && targets.any { target ->
        !target.finished && (!target.startConfirmed || isComplete(target.total))
    }
}

internal enum class RepeatAction {
    Forget,
    Start,
    Count,
    ConfirmStart,
    Wait,
}

internal fun repeatAction(target: RepeatTarget, remote: RemoteRepeat, complete: Boolean): RepeatAction {
    val current = remote.count ?: return RepeatAction.Forget
    val expected = target.targetCount ?: return RepeatAction.Forget
    if (current != expected - 1 || remote.status == RepeatStatus.Other) return RepeatAction.Forget
    if (remote.status == RepeatStatus.Completed && target.startConfirmed) return RepeatAction.Forget
    return when {
        complete -> RepeatAction.Count
        remote.status == RepeatStatus.Completed -> RepeatAction.Start
        !target.startConfirmed -> RepeatAction.ConfirmStart
        else -> RepeatAction.Wait
    }
}

internal fun Double.toRepeatPart(): Int? {
    val number = toInt()
    return number.takeIf { it > 0 && it.toDouble() == this }
}
