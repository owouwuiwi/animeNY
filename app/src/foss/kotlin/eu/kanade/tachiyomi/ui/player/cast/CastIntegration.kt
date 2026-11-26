@file:Suppress("UNUSED_PARAMETER")

package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.core.common.preference.PreferenceStore

internal const val GOOGLE_CAST_AVAILABLE = false

internal fun createPlayerCastController(
    activity: ComponentActivity,
    preferences: PreferenceStore,
): PlayerCastController = NoCastController

private object NoCastController : PlayerCastController {
    override fun onPlayerReady(): Boolean = false
    override fun onResume() = Unit
    override fun onPause() = Unit
    override fun onDestroy() = Unit
    override fun onMediaSessionPaused(inPictureInPicture: Boolean) = Unit
}

@Composable
internal fun rememberCastMiniControls(): @Composable (Modifier) -> Unit = {}

@Composable
internal fun CastRouteButton(modifier: Modifier) = Unit

internal fun sendEpisodeToCast(
    context: Context,
    title: String,
    episode: String,
    lastSecondSeen: Long,
    image: String,
    videoUrl: String,
) = Unit
