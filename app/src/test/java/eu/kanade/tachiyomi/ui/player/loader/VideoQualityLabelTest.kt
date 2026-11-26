package eu.kanade.tachiyomi.ui.player.loader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VideoQualityLabelTest {
    @Test
    fun `Resolution is read from common descriptive labels`() {
        videoQualityHeight("HD 720P | MP4") shouldBe 720
        videoQualityHeight("1920×1080 WEB-DL") shouldBe 1080
        videoQualityHeight("Torrent • 4K UHD") shouldBe 2160
    }

    @Test
    fun `Episode and codec numbers do not masquerade as resolution`() {
        videoQualityHeight("Episode 1080 | x265 | HEVC") shouldBe null
    }
}
