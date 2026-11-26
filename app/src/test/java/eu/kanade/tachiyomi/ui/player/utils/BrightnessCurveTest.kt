package eu.kanade.tachiyomi.ui.player.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class BrightnessCurveTest {
    @Test
    fun `Perceptual steps are finer at low brightness and retain the endpoints`() {
        0f.brightnessFromGestureSpace(true) shouldBe 0f
        1f.brightnessFromGestureSpace(true) shouldBe 1f
        (0.1f.brightnessFromGestureSpace(true) < 0.1f) shouldBe true
    }

    @Test
    fun `Starting a new gesture retains the current physical brightness`() {
        for (physical in listOf(0.01f, 0.15f, 0.5f, 0.9f)) {
            val adjusted = physical.brightnessToGestureSpace(true).brightnessFromGestureSpace(true)
            (abs(adjusted - physical) < 0.0001f) shouldBe true
        }
    }

    @Test
    fun `Linear gestures and the dimming overlay retain their existing values`() {
        0.25f.brightnessFromGestureSpace(false) shouldBe 0.25f
        (-0.5f).brightnessToGestureSpace(true) shouldBe -0.5f
        (-0.5f).brightnessFromGestureSpace(true) shouldBe -0.5f
    }
}
