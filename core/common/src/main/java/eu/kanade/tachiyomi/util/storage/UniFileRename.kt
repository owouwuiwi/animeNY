package eu.kanade.tachiyomi.util.storage

import com.hippo.unifile.UniFile
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Renames this file, falling back to copy and delete for providers that reject renames
 * (e.g. HarmonyOS), whether they return false or throw. Returns the renamed file, or null on
 * failure, in which case the original is left in place.
 */
fun UniFile.renameToOrCopy(displayName: String): UniFile? {
    if (name == displayName) return takeIf { exists() }
    val parent = parentFile
    if (parent?.findFile(displayName) != null) return null

    val renamed = try {
        renameTo(displayName)
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "Failed to rename $name to $displayName, copying instead" }
        false
    }
    if (renamed) return this
    if (parent == null) return null
    parent.findFile(displayName)?.let { target ->
        // Some providers rename and still report a failure; the original is gone then
        if (exists()) return null
        logcat(LogPriority.WARN) { "Rename of $name to $displayName reported a failure but happened" }
        return target
    }

    val copy = try {
        copyInto(parent, displayName)
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e) { "Failed to copy $name to $displayName" }
        null
    }
    if (copy == null) return null
    delete()
    return copy
}

private fun UniFile.copyInto(parent: UniFile, name: String): UniFile? {
    if (parent.findFile(name) != null) return null
    val children = if (isDirectory) listFiles() ?: return null else null
    val target = if (children != null) parent.createDirectory(name) else parent.createFile(name)
    if (target == null) return null

    var copied = false
    try {
        if (children != null) {
            for (child in children) {
                child.copyInto(target, child.name ?: return null) ?: return null
            }
        } else {
            openInputStream().use { input ->
                target.openOutputStream().use { output -> input.copyTo(output) }
            }
        }
        copied = true
        return target
    } finally {
        if (!copied) target.delete()
    }
}
