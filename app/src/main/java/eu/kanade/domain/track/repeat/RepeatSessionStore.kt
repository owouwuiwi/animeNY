package eu.kanade.domain.track.repeat

import android.content.Context
import android.util.AtomicFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.withIOContext
import java.io.File
import java.io.FileNotFoundException

/** Commits the intended count before the first network request, including across process death. */
@Inject
@SingleIn(AppScope::class)
internal class RepeatSessionStore(context: Context, private val json: Json) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "tracked-repeat-sessions-v1.json"))
    private val mutex = Mutex()
    private var sessions: List<RepeatSession>? = null

    private fun load(): List<RepeatSession> {
        return sessions ?: try {
            json.decodeFromString<List<RepeatSession>>(file.readFully().decodeToString())
        } catch (_: FileNotFoundException) {
            emptyList()
        }.also { sessions = it }
    }

    suspend fun all(): List<RepeatSession> = withIOContext { mutex.withLock { load() } }

    suspend fun edit(key: RepeatKey, update: (RepeatSession?) -> RepeatSession?): RepeatSession? = withIOContext {
        mutex.withLock {
            val sessions = load()
            val previous = sessions.firstOrNull { it.key == key }
            val next = update(previous)
            if (previous != next) {
                val updated = sessions.filterNot { it.key == key } + listOfNotNull(next)
                val bytes = json.encodeToString(updated).encodeToByteArray()
                val stream = file.startWrite()
                try {
                    stream.write(bytes)
                    file.finishWrite(stream)
                } catch (e: Exception) {
                    file.failWrite(stream)
                    throw e
                }
                this@RepeatSessionStore.sessions = updated
            }
            next
        }
    }
}
