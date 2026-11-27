package eu.kanade.tachiyomi.data.download.anime

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.renameToOrCopy
import tachiyomi.core.common.storage.extension
import java.io.File

/**
 * Publishes a finished episode. An older interrupted copy may leave a directory at the final
 * name. A confirmed empty local directory can be removed nonrecursively. Other destinations
 * leave both the existing files and the finished staging directory available for recovery.
 */
internal fun finalizeEpisodeDir(animeDir: UniFile, tmpDir: UniFile, dirname: String): UniFile? {
    val destination = animeDir.findFile(dirname)
    if (destination != null) {
        val path = destination.filePath ?: return null
        if (!destination.isDirectory || destination.listFiles()?.isEmpty() != true || !File(path).delete()) {
            return null
        }
    }
    return tmpDir.renameToOrCopy(dirname)
}

/** Renames a download directory or a legacy standalone video within its anime directory. */
internal fun UniFile.renameDownloadedEpisode(targetStem: String): Boolean {
    val targetName = if (isFile) {
        val suffix = extension ?: return false
        "$targetStem.$suffix"
    } else {
        targetStem
    }
    val parent = parentFile ?: return false
    return parent.findFile(targetName) == null && renameTo(targetName)
}
