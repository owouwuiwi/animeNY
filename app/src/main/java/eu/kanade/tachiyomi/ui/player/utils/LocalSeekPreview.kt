package eu.kanade.tachiyomi.ui.player.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal fun localSeekPreviewUri(url: String): Uri? {
    val uri = Uri.parse(url)
    return when (uri.scheme) {
        "file", "content" -> uri
        null -> url.takeIf { it.startsWith("/") }?.let { Uri.fromFile(File(it)) }
        else -> null
    }
}

internal class LocalSeekPreview {
    private val retrieverLock = Mutex()
    private val cache = object : LruCache<Long, Bitmap>(CACHE_LIMIT_BYTES) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
    }
    private var cachedUri: Uri? = null
    private var cachedPlaybackId: Int? = null

    /**
     * The current position is conflated while decoding. If the user starts a new drag while
     * the previous native decode is finishing, wait for its retriever to be released.
     */
    fun frames(context: Context, uri: Uri, playbackId: Int, positions: StateFlow<Float>): Flow<Bitmap> = flow {
        retrieverLock.withLock {
            if (cachedUri != uri || cachedPlaybackId != playbackId) {
                cache.evictAll()
                cachedUri = uri
                cachedPlaybackId = playbackId
            }
            var retriever: MediaMetadataRetriever? = null
            try {
                positions
                    .map { it.toLong().coerceAtLeast(0L) }
                    .distinctUntilChanged()
                    .collect { second ->
                        cache.get(second)?.let {
                            emit(it)
                            return@collect
                        }
                        val startedAt = SystemClock.elapsedRealtime()
                        val decoder = retriever ?: MediaMetadataRetriever().also {
                            retriever = it
                            it.setDataSource(context, uri)
                        }
                        decoder.decodeFrame(second)?.let {
                            cache.put(second, it)
                            emit(it)
                        }
                        val elapsed = SystemClock.elapsedRealtime() - startedAt
                        delay(MIN_DECODE_INTERVAL_MILLIS - elapsed)
                    }
            } finally {
                retriever?.release()
            }
        }
    }

    private fun MediaMetadataRetriever.decodeFrame(second: Long): Bitmap? {
        val timestamp = second * 1_000_000L
        val option = MediaMetadataRetriever.OPTION_CLOSEST
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getScaledFrameAtTime(timestamp, option, 320, 180)
        } else {
            getFrameAtTime(timestamp, option)
        }
    }

    private companion object {
        const val CACHE_LIMIT_BYTES = 8 * 1024 * 1024
        const val MIN_DECODE_INTERVAL_MILLIS = 125L
    }
}
