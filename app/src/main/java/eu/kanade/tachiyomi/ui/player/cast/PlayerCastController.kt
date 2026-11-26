package eu.kanade.tachiyomi.ui.player.cast

internal interface PlayerCastController {
    /** Attaches the player to any existing Cast session and returns whether it is connected. */
    fun onPlayerReady(): Boolean
    fun onResume()
    fun onPause()
    fun onDestroy()
    fun onMediaSessionPaused(inPictureInPicture: Boolean)
}
