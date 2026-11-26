package eu.kanade.tachiyomi.ui.player.utils

import kotlin.math.pow

private const val BRIGHTNESS_GAMMA = 2.2f

// Negative values control the existing dimming overlay rather than the display brightness.
internal fun Float.brightnessToGestureSpace(perceptual: Boolean): Float {
    return if (perceptual && this > 0f) coerceAtMost(1f).pow(1f / BRIGHTNESS_GAMMA) else this
}

internal fun Float.brightnessFromGestureSpace(perceptual: Boolean): Float {
    return if (perceptual && this > 0f) coerceAtMost(1f).pow(BRIGHTNESS_GAMMA) else this
}
