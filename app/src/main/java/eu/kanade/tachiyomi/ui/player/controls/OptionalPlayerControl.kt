package eu.kanade.tachiyomi.ui.player.controls

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR

enum class OptionalPlayerControl(val titleRes: StringResource) {
    AutoPlay(TLMR.strings.player_control_autoplay),
    Subtitles(TLMR.strings.cast_subtitles),
    Audio(TLMR.strings.cast_audio_tracks),
    Quality(TLMR.strings.player_control_quality),
    More(MR.strings.label_more),
    PlaybackSpeed(TLMR.strings.playback_speed),
    Rotation(AYMR.strings.pref_category_player_orientation),
    PictureInPicture(AYMR.strings.pref_category_pip),
    AspectRatio(TLMR.strings.player_control_aspect_ratio),
}
