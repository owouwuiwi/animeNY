package eu.kanade.tachiyomi.ui.browse.manga.migration.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.source.interactor.filterSourcesByName
import eu.kanade.presentation.browse.manga.MigrateMangaSourceScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabSearch
import eu.kanade.tachiyomi.ui.browse.manga.migration.manga.MigrateMangaScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Help
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.migrateMangaSourceTab(): TabContent {
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<MigrateMangaSourceViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val sources = remember(state.items, searchQuery) {
        state.items.filterSourcesByName(searchQuery) { it.first.name }
    }

    return TabContent(
        titleRes = MR.strings.label_migration,
        searchEnabled = true,
        search = TabSearch(searchQuery) { searchQuery = it },
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.migration_help_guide),
                icon = MaterialSymbols.AutoMirroredRounded.Help,
                onClick = {
                    uriHandler.openUri("https://aniyomi.org/help/guides/source-migration/")
                },
            ),
        ),
        content = { contentPadding, _ ->
            MigrateMangaSourceScreen(
                state = state.copy(items = sources),
                hasSearchQuery = !searchQuery.isNullOrBlank(),
                contentPadding = contentPadding,
                onClickItem = { source ->
                    navigator.push(MigrateMangaScreen(source.id))
                },
                onToggleSortingDirection = viewModel::toggleSortingDirection,
                onToggleSortingMode = viewModel::toggleSortingMode,
            )
        },
    )
}
