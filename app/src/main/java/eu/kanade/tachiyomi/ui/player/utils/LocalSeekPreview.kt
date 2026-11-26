package eu.kanade.tachiyomi.ui.player.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File

internal fun localSeekPreviewUri(url: String): Uri? {
    val uri = Uri.parse(url)
    return when (uri.scheme) {
        "file", "content" -> uri
        null -> url.takeIf { it.startsWith("/") }?.let { Uri.fromFile(File(it)) }
        else -> null
    }
}

/**
 * Reads frames with a single retriever per gesture. The input StateFlow keeps the
 * newest position while decoding, so moving the slider never queues old requests.
 */
internal fun localSeekPreviewFrames(context: Context, uri: Uri, positions: Flow<Float>): Flow<Bitmap> = flow {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        positions
            .map { it.toLong().coerceAtLeast(0L) }
            .distinctUntilChanged()
            .collect { second ->
                val timestamp = second * 1_000_000L
                val option = MediaMetadataRetriever.OPTION_CLOSEST
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timestamp, option, 320, 180)
                } else {
                    retriever.getFrameAtTime(timestamp, option)
                }
                if (frame != null) emit(frame)
                delay(125L)
            }
    } finally {
        retriever.release()
    }
}
