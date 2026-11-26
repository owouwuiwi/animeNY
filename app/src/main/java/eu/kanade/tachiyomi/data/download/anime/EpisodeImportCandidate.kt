package eu.kanade.tachiyomi.data.download.anime

import android.net.Uri
import tachiyomi.domain.items.episode.model.Episode

internal data class EpisodeImportCandidate(val uri: Uri, val fileName: String, val episode: Episode?)
