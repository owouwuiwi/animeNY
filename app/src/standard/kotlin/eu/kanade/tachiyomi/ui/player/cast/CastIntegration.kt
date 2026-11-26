package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.images.WebImage
import eu.kanade.tachiyomi.ui.player.CastManager
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.PreferenceStore

internal const val GOOGLE_CAST_AVAILABLE = true

internal fun createPlayerCastController(
    activity: ComponentActivity,
    preferences: PreferenceStore,
): PlayerCastController = GooglePlayerCastController(CastManager(activity, preferences))

private class GooglePlayerCastController(private val manager: CastManager) : PlayerCastController {
    override fun onPlayerReady(): Boolean = with(manager) {
        registerSessionListener()
        if (castState.value == CastManager.CastState.CONNECTED) {
            updateCastState(CastManager.CastState.CONNECTED)
        }
        castState.value == CastManager.CastState.CONNECTED
    }

    override fun onResume() {
        manager.reconnect()
        manager.registerSessionListener()
    }

    override fun onPause() = manager.maintainCastSessionBackground()

    override fun onDestroy() = manager.cleanup()

    override fun onMediaSessionPaused(inPictureInPicture: Boolean) {
        if (!inPictureInPicture) {
            manager.unregisterSessionListener()
        }
        if (manager.castState.value == CastManager.CastState.CONNECTED) {
            manager.maintainCastSessionBackground()
        }
    }
}

@Composable
internal fun rememberCastMiniControls(): @Composable (Modifier) -> Unit {
    val activity = LocalContext.current as? ComponentActivity ?: return {}
    val manager = remember(activity) { CastManager(activity, activity.appGraph.preferenceStore) }

    LaunchedEffect(manager) {
        manager.startDeviceDiscovery()
        manager.reconnect()
    }

    return { modifier ->
        val state by manager.castState.collectAsState()
        AnimatedVisibility(
            visible = state == CastManager.CastState.CONNECTED,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            CastMiniController(castManager = manager, modifier = modifier)
        }
    }
}

@Composable
internal fun CastRouteButton(modifier: Modifier) {
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
                dialogFactory = CustomCastThemeFactory()
            }
        },
        modifier = modifier,
    )
}

internal fun sendEpisodeToCast(
    context: Context,
    title: String,
    episode: String,
    lastSecondSeen: Long,
    image: String,
    videoUrl: String,
) {
    val castSession = CastContext.getSharedInstance(context).sessionManager.currentCastSession
    val remoteMediaClient = castSession?.remoteMediaClient
    if (castSession == null || !castSession.isConnected) {
        Toast.makeText(context, "Cast is not connected", Toast.LENGTH_SHORT).show()
        return
    }
    if (remoteMediaClient != null) {
        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_SUBTITLE, episode)
            addImage(WebImage(Uri.parse(image)))
        }
        val contentType = when {
            videoUrl.contains(".m3u8") -> "application/x-mpegURL"
            videoUrl.contains(".mpd") -> "application/dash+xml"
            else -> "video/mp4"
        }
        val mediaInfo = MediaInfo.Builder(videoUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(mediaMetadata)
            .build()
        val mediaQueueItem = MediaQueueItem.Builder(mediaInfo)
            .setAutoplay(true)
            .setStartTime(lastSecondSeen.toDouble() / 1000)
            .build()
        val mediaStatus = remoteMediaClient.mediaStatus
        if (mediaStatus != null && mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING) {
            // Si hay un video reproduciéndose, agregar el nuevo video a la cola
            remoteMediaClient.queueAppendItem(mediaQueueItem, null)
        } else {
            // Si no hay un video reproduciéndose, cargar el video directamente
            val mediaLoadRequestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setCurrentTime(lastSecondSeen)
                .build()
            remoteMediaClient.load(mediaLoadRequestData)
        }
    } else {
        Toast.makeText(context, "remoteMediaClient is null", Toast.LENGTH_SHORT).show()
    }
}
