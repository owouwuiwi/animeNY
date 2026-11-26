package eu.kanade.tachiyomi.data.backup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.zacsweers.metro.Inject
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import logcat.LogPriority
import okio.buffer
import okio.source
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.repository.MangaRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@Inject
class MangaCoverBackup(
    private val covers: MangaCoverCache,
    private val mangaRepository: MangaRepository,
    private val updateManga: UpdateManga,
) {
    fun read(mangaId: Long): ByteArray? {
        val file = covers.getCustomCoverFile(mangaId)
        if (!file.isFile) return null
        return try {
            val original = if (file.length() <= MAX_CUSTOM_COVER_BYTES) {
                file.source().buffer().use { source ->
                    if (source.request(MAX_CUSTOM_COVER_BYTES + 1)) null else source.readByteArray()
                }
            } else {
                null
            }
            val image = original ?: scaleOversizedCover(file)
            if (image == null || image.isEmpty()) {
                logcat(LogPriority.WARN) { "Custom manga cover could not be added to backup" }
            }
            image?.takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Could not read custom manga cover for backup" }
            null
        }
    }

    suspend fun restore(sourceId: Long, url: String, image: ByteArray) {
        val manga = mangaRepository.getMangaByUrlAndSourceId(url, sourceId) ?: return
        if (!manga.favorite || covers.getCustomCoverFile(manga.id).exists()) return
        if (image.size.toLong() !in 1L..MAX_CUSTOM_COVER_BYTES ||
            ImageUtil.findImageType { image.inputStream() } == null
        ) {
            throw IOException("Invalid or oversized custom manga cover")
        }
        if (covers.restoreCustomCoverIfMissing(manga.id, image)) {
            updateManga.awaitUpdateCoverLastModified(manga.id)
        }
    }

    private fun scaleOversizedCover(file: File): ByteArray? {
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, size)
        if (size.outWidth <= 0 || size.outHeight <= 0) return null
        val longestSide = maxOf(size.outWidth, size.outHeight)
        var sampleSize = 1
        while (longestSide / sampleSize > MAX_SCALED_COVER_SIDE) sampleSize *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null
        return try {
            ByteArrayOutputStream().use { output ->
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) output.toByteArray() else null
            }?.takeIf { it.size.toLong() <= MAX_CUSTOM_COVER_BYTES }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val MAX_SCALED_COVER_SIDE = 1024
    }
}

internal const val MAX_CUSTOM_COVER_BYTES = 16L * 1024 * 1024
