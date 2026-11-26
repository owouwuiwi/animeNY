package mihon.feature.migration.list

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/** An incompatible installed extension must not abort migration of other titles or sources. */
internal inline fun <T> compatibleMigrationSourceCall(sourceId: Long, call: () -> T): T? {
    return try {
        call()
    } catch (error: LinkageError) {
        logcat(LogPriority.WARN, error) { "Incompatible source $sourceId during migration" }
        null
    }
}
