package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeBackupCreator
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime

class BackupAnimeViewerFlagsTest {
    @Test
    fun `old archives restore intro length without inventing other flags or an airing time`() {
        // The old writer retained only the low byte of the timestamp, including its sign bit.
        val legacy = LegacyAnime(source = 1, url = "/legacy", favorite = false, viewerFlags = 0xA023071C.toInt())
        val encoded = ProtoBuf.encodeToByteArray(LegacyAnime.serializer(), legacy)
        val restored = ProtoBuf.decodeFromByteArray(BackupAnime.serializer(), encoded).getAnimeImpl()

        assertEquals(28, restored.skipIntroLength)
        assertFalse(restored.skipIntroDisable)
        assertEquals(0, restored.nextEpisodeToAir)
        assertEquals(0L, restored.nextEpisodeAiringAt)
    }

    @Test
    fun `new archives retain viewer settings and older readers still see the intro length`() = runTest {
        val airingCache = (1_795_800_000L shl Anime.ANIME_AIRING_TIME_SHIFT) or (7L shl 8)
        val disableIntro = Anime.ANIME_INTRO_DISABLE_MASK
        val creator = AnimeBackupCreator(mockk(), mockk(), mockk(), mockk())
        val anime = listOf(
            Anime.create().copy(id = 1, url = "/disabled", viewerFlags = disableIntro or airingCache),
            Anime.create().copy(id = 2, url = "/duration", viewerFlags = 28L or airingCache),
            Anime.create().copy(id = 3, url = "/rebacked", viewerFlags = 0xA023071C.toInt().toLong()),
        )
        val options = BackupOptions(categories = false, chapters = false, tracking = false, history = false)

        val bytes = Buffer()
        val writer = BackupWriter(bytes, ProtoBuf)
        creator(anime, options).toList().forEach(writer::writeAnimeEntry)
        val modernReader = BackupReader(bytes.clone(), ProtoBuf)
        val legacyReader = BackupReader(bytes, ProtoBuf)

        for ((settings, legacySettings) in listOf(disableIntro to 0, 28L to 28, 28L to 28)) {
            assertTrue(modernReader.nextEntry())
            assertTrue(legacyReader.nextEntry())
            assertEquals(settings, modernReader.readAnimeEntry().getAnimeImpl().viewerFlags)
            assertEquals(legacySettings, legacyReader.readEntry(LegacyAnime.serializer()).viewerFlags)
        }
        assertFalse(modernReader.nextEntry())
    }

    @Serializable
    private data class LegacyAnime(
        @ProtoNumber(1) val source: Long,
        @ProtoNumber(2) val url: String,
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(103) val viewerFlags: Int = 0,
    )
}
