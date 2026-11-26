package eu.kanade.domain.track.repeat

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.UpsertAnimeTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.UpsertMangaTrack
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
class TrackRepeats internal constructor(
    private val context: Context,
    private val store: RepeatSessionStore,
    private val trackerManager: TrackerManager,
    private val trackPreferences: TrackPreferences,
    private val basePreferences: BasePreferences,
    private val getAnimeTracks: GetAnimeTracks,
    private val getMangaTracks: GetMangaTracks,
    private val upsertAnimeTrack: UpsertAnimeTrack,
    private val upsertMangaTrack: UpsertMangaTrack,
    private val getAnime: GetAnime,
    private val getManga: GetManga,
    private val getAnimeIncognito: GetAnimeIncognitoState,
    private val getMangaIncognito: GetMangaIncognitoState,
) {
    val enabled: Boolean
        get() = trackPreferences.trackRepeats.get() &&
            trackPreferences.autoUpdateTrack.get() &&
            !basePreferences.incognitoMode.get()

    fun enabledFlow(): Flow<Boolean> = combine(
        trackPreferences.trackRepeats.changes(),
        trackPreferences.autoUpdateTrack.changes(),
        basePreferences.incognitoMode.changes(),
    ) { repeat, autoTrack, incognito -> repeat && autoTrack && !incognito }.distinctUntilChanged()

    internal suspend fun playback(key: RepeatKey): RepeatPlayback? {
        return try {
            if (!mayTrack(key)) return null
            val session = store.all().firstOrNull { it.key == key && it.nextPart() != null }
            session?.let { RepeatPlayback(this, it.key, it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not load the tracked repeat" }
            null
        }
    }

    internal fun available(key: RepeatKey, parts: Set<Int>): Flow<Boolean> = combine(
        enabledFlow(),
        localTracksFlow(key),
        trackerManager.loggedInTrackersFlow(),
    ) { enabled, tracks, _ ->
        enabled && 1 in parts && mayTrack(key) &&
            (store.all().any { it.key == key } || tracks.any { toTarget(it, parts) != null })
    }.distinctUntilChanged()

    internal suspend fun prepare(key: RepeatKey, parts: Map<Int, Long>): Long? {
        if (!mayTrack(key) || 1 !in parts) return null
        val prior = store.all().firstOrNull { it.key == key }
        if (prior != null) {
            resumeSync()
            return prior.nextPart()?.let(parts::get)
        }

        val targets = localTracksFlow(key).first().mapNotNull { toTarget(it, parts.keys) }
        if (targets.isEmpty()) return null
        val session = store.edit(key) { existing ->
            existing ?: RepeatSession(UUID.randomUUID().toString(), key, targets)
        }
        return session?.nextPart()?.let(parts::get)
    }

    internal suspend fun record(key: RepeatKey, sessionId: String, part: Int, completed: Boolean): Boolean {
        if (!mayTrack(key)) return false
        var schedule = false
        var accepted = false
        store.edit(key) { current ->
            if (current?.id != sessionId || (!current.started && part != 1)) {
                current
            } else {
                val updated = current.record(part, completed)
                accepted = part in 1..current.total
                schedule = (!current.started && updated.started) || current.targets.any { target ->
                    !current.isComplete(target.total) && updated.isComplete(target.total)
                }
                updated
            }
        }
        if (schedule) RepeatSyncJob.schedule(context)
        return accepted
    }

    suspend fun resumeSync() {
        if (!enabled) return
        try {
            if (store.all().any(RepeatSession::requiresSync)) {
                RepeatSyncJob.schedule(context)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not resume tracked repeats" }
        }
    }

    suspend fun sync(): Boolean {
        if (!enabled) return true
        var success = true
        for (session in store.all().filter(RepeatSession::requiresSync)) {
            if (!mayTrack(session.key)) continue
            if (!isCurrentEntry(session.key)) {
                store.edit(session.key) { null }
                continue
            }
            val tracks = localTracksFlow(session.key).first()
            for (target in session.targets) {
                if (target.finished || (target.startConfirmed && !session.isComplete(target.total))) continue
                try {
                    syncTarget(session, target, tracks)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Could not synchronize a tracked repeat" }
                    success = false
                }
            }
        }
        return success
    }

    private suspend fun syncTarget(session: RepeatSession, target: RepeatTarget, tracks: List<LocalTrack>) {
        val tracker = trackerManager.get(target.trackerId)
        val track = tracks.find {
            it.trackId == target.trackId && it.trackerId == target.trackerId && it.remoteId == target.remoteId
        }
        if (tracker !is RepeatTracker || track == null) {
            saveTarget(session, target.copy(finished = true))
            return
        }
        val repeating = when (session.key.kind) {
            RepeatKind.Anime -> tracker.animeService.getRewatchingStatus()
            RepeatKind.Manga -> tracker.mangaService.getRereadingStatus()
        }
        if (track.status != tracker.getCompletionStatus() && track.status != repeating) {
            saveTarget(session, target.copy(finished = true))
            return
        }
        if (!tracker.matchesAccount(target)) return

        val remote = tracker.getRepeat(session.key.kind, target.remoteId)
        if (!tracker.matchesAccount(target) || !mayTrack(session.key)) return
        val count = remote?.count
        if (remote == null || count == null || count < 0) {
            saveTarget(session, target.copy(finished = true))
            return
        }
        val pending = if (target.targetCount == null) {
            if (count >= tracker.maxRepeatCount) {
                saveTarget(session, target.copy(finished = true))
                return
            }
            target.copy(targetCount = count + 1).also { saveTarget(session, it) }
        } else {
            target
        }
        val targetCount = checkNotNull(pending.targetCount)

        when (repeatAction(pending, remote, session.isComplete(target.total))) {
            RepeatAction.Forget -> {
                if (remote.status == RepeatStatus.Completed) {
                    updateLocalStatus(session.key, tracker, target, RepeatStatus.Completed)
                }
                saveTarget(session, pending.copy(finished = true))
            }

            RepeatAction.Start -> {
                if (!mayTrack(session.key) || !tracker.matchesAccount(target)) return
                val saved = tracker.updateRepeat(session.key.kind, remote, RepeatStatus.Repeating, null)
                check(saved.status == RepeatStatus.Repeating) { "Tracker did not confirm the repeat start" }
                updateLocalStatus(session.key, tracker, target, RepeatStatus.Repeating)
                saveTarget(session, pending.copy(startConfirmed = true))
            }

            RepeatAction.Count -> {
                if (!mayTrack(session.key) || !tracker.matchesAccount(target)) return
                val saved = tracker.updateRepeat(session.key.kind, remote, RepeatStatus.Completed, targetCount)
                check(saved.status == RepeatStatus.Completed && (saved.count ?: -1) >= targetCount) {
                    "Tracker did not confirm the repeat completion"
                }
                updateLocalStatus(session.key, tracker, target, RepeatStatus.Completed)
                saveTarget(session, pending.copy(finished = true))
            }

            RepeatAction.ConfirmStart -> {
                updateLocalStatus(session.key, tracker, target, RepeatStatus.Repeating)
                saveTarget(session, pending.copy(startConfirmed = true))
            }

            RepeatAction.Wait -> Unit
        }
    }

    private suspend fun saveTarget(session: RepeatSession, updated: RepeatTarget) {
        store.edit(session.key) { current ->
            if (current == null || current.id != session.id) {
                current
            } else {
                val targets = current.targets.map { if (it.trackId == updated.trackId) updated else it }
                current.copy(targets = targets).takeUnless { updatedSession ->
                    updatedSession.targets.all { it.finished }
                }
            }
        }
    }

    private suspend fun isCurrentEntry(key: RepeatKey): Boolean = when (key.kind) {
        RepeatKind.Anime -> getAnime.await(key.id)?.repeatKey() == key
        RepeatKind.Manga -> getManga.await(key.id)?.repeatKey() == key
    }

    private suspend fun mayTrack(key: RepeatKey) = enabled && when (key.kind) {
        RepeatKind.Anime -> !getAnimeIncognito.await(key.source)
        RepeatKind.Manga -> !getMangaIncognito.await(key.source)
    }

    private fun localTracksFlow(key: RepeatKey): Flow<List<LocalTrack>> = when (key.kind) {
        RepeatKind.Anime -> getAnimeTracks.subscribe(key.id).map { tracks ->
            tracks.map { LocalTrack(it.id, it.trackerId, it.remoteId, it.status, it.totalEpisodes) }
        }

        RepeatKind.Manga -> getMangaTracks.subscribe(key.id).map { tracks ->
            tracks.map { LocalTrack(it.id, it.trackerId, it.remoteId, it.status, it.totalChapters) }
        }
    }

    private fun toTarget(track: LocalTrack, parts: Set<Int>): RepeatTarget? {
        val tracker = trackerManager.get(track.trackerId)
        if (tracker !is RepeatTracker || !tracker.isLoggedIn ||
            track.status != tracker.getCompletionStatus() || track.total !in 1L..parts.size.toLong()
        ) {
            return null
        }

        val total = track.total.toInt()
        if (!(1..total).all(parts::contains)) return null
        val account = tracker.getUsername().takeIf(String::isNotBlank) ?: return null
        return RepeatTarget(track.trackerId, track.trackId, track.remoteId, account, total)
    }

    private suspend fun updateLocalStatus(
        key: RepeatKey,
        tracker: Tracker,
        target: RepeatTarget,
        status: RepeatStatus,
    ) {
        if (!tracker.matchesAccount(target)) return
        val done = tracker.getCompletionStatus()
        when (key.kind) {
            RepeatKind.Anime -> {
                val track = getAnimeTracks.subscribe(key.id).first().find { it.id == target.trackId } ?: return
                val repeating = tracker.animeService.getRewatchingStatus()
                if (track.remoteId == target.remoteId && track.status in setOf(done, repeating)) {
                    upsertAnimeTrack.await(
                        track.copy(
                            status = if (status ==
                                RepeatStatus.Completed
                            ) {
                                done
                            } else {
                                repeating
                            },
                        ),
                    )
                }
            }

            RepeatKind.Manga -> {
                val track = getMangaTracks.subscribe(key.id).first().find { it.id == target.trackId } ?: return
                val repeating = tracker.mangaService.getRereadingStatus()
                if (track.remoteId == target.remoteId && track.status in setOf(done, repeating)) {
                    upsertMangaTrack.await(
                        track.copy(
                            status = if (status ==
                                RepeatStatus.Completed
                            ) {
                                done
                            } else {
                                repeating
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun Tracker.matchesAccount(target: RepeatTarget) = isLoggedIn && getUsername() == target.account

    private data class LocalTrack(
        val trackId: Long,
        val trackerId: Long,
        val remoteId: Long,
        val status: Long,
        val total: Long,
    )
}

internal fun Anime.repeatKey() = RepeatKey(RepeatKind.Anime, id, source, url)

internal fun Manga.repeatKey() = RepeatKey(RepeatKind.Manga, id, source, url)
