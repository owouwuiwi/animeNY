package eu.kanade.tachiyomi.ui.player.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private var nextDecodeAt = 0L

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

            suspend fun decode(second: Long, option: Int): Bitmap? {
                val waitMillis = nextDecodeAt - SystemClock.elapsedRealtime()
                val moved = waitMillis > 0 && withTimeoutOrNull(waitMillis) {
                    positions.first { it.previewSecond() != second }
                } != null
                if (moved) return null
                currentCoroutineContext().ensureActive()
                if (positions.value.previewSecond() != second) return null

                val decoder = retriever ?: MediaMetadataRetriever().also {
                    retriever = it
                    it.setDataSource(context, uri)
                }
                nextDecodeAt = SystemClock.elapsedRealtime() + MIN_DECODE_INTERVAL_MILLIS
                return decoder.decodeFrame(second, option)
            }

            try {
                positions
                    .map { it.previewSecond() }
                    .distinctUntilChanged()
                    .collect { second ->
                        cache.get(second)?.let {
                            if (positions.value.previewSecond() == second) emit(it)
                            return@collect
                        }
                        // Two passes are only affordable when the retriever can scale its output.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            decode(second, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                                if (positions.value.previewSecond() == second) emit(it)
                            }
                        }

                        decode(second, MediaMetadataRetriever.OPTION_CLOSEST)?.let {
                            cache.put(second, it)
                            if (positions.value.previewSecond() == second) emit(it)
                        }
                    }
            } finally {
                retriever?.release()
            }
        }
    }

    private fun Float.previewSecond() = toLong().coerceAtLeast(0L)

    private fun MediaMetadataRetriever.decodeFrame(second: Long, option: Int): Bitmap? {
        val timestamp = second * 1_000_000L
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
