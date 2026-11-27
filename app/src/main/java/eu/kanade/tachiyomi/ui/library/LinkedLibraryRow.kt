package eu.kanade.tachiyomi.ui.library

import tachiyomi.domain.entries.merge.interactor.MergedEntry
import tachiyomi.domain.entries.merge.model.EntryMerge

data class LinkedLibraryRow<E, P>(
    val group: EntryMerge,
    val snapshot: MergedEntry<E, P>,
    val favoriteMembers: List<E>,
    val aliases: List<LibrarySearchAlias<E>>,
)

data class LibrarySearchAlias<E>(
    val entry: E,
    val sourceName: String,
    val sourceLanguage: String,
)
