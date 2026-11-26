package mihon.feature.upcoming.anime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import mihon.feature.upcoming.anime.components.UpcomingItem
import mihon.feature.upcoming.components.UpcomingAgendaScreen
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

class UpcomingAnimeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = metroViewModel<UpcomingAnimeViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        when (state.dialog) {
            is UpcomingAnimeViewModel.Dialog.FilterSheet -> {
                UpcomingFilterDialog(
                    viewModel = viewModel,
                )
            }

            null -> {}
        }

        UpcomingAgendaScreen(
            title = stringResource(TLMR.strings.upcoming_anime_title),
            days = state.days,
            hasActiveFilters = state.hasActiveFilters,
            onFilterClick = viewModel::showFilterDialog,
            entryKey = { it.anime.id },
        ) { entry ->
            UpcomingItem(
                upcoming = entry.anime,
                schedule = entry.schedule,
                onClick = { navigator.push(AnimeScreen(entry.anime.id)) },
            )
        }
    }
}

@Composable
private fun ColumnScope.CategoryFilterSheet(
    viewModel: UpcomingAnimeViewModel,
) {
    Text(
        stringResource(MR.strings.pref_filter_upcoming_categories_details),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
    )

    HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.extraSmall))

    val allCategories by viewModel.getCategories.subscribe().collectAsState(initial = emptyList())

    if (allCategories.isEmpty()) {
        LoadingScreen(modifier = Modifier.padding(16.dp))
        return
    }

    val excluded by viewModel.upcomingPreferences.animeFilterExcludedCategories.collectAsState()
    val included by viewModel.upcomingPreferences.animeFilterIncludedCategories.collectAsState()

    val selected = remember(allCategories, included, excluded) {
        allCategories.map { category ->
            when (category.id) {
                in included -> TriState.ENABLED_IS
                in excluded -> TriState.ENABLED_NOT
                else -> TriState.DISABLED
            }
        }.toMutableStateList()
    }

    Column {
        allCategories.fastForEachIndexed { idx, category ->
            val state = selected[idx]
            TriStateItem(
                label = category.visualName,
                state = state,
                onClick = {
                    selected[idx] = state.next()
                    viewModel.cycleCategory(category)
                },
            )
        }
    }
}

@Composable
fun UpcomingFilterDialog(
    viewModel: UpcomingAnimeViewModel,
) {
    val showConfirmedOnly = viewModel.upcomingPreferences.animeConfirmedAiringOnly
    val confirmedOnly by showConfirmedOnly.collectAsState()
    TabbedDialog(
        onDismissRequest = viewModel::resetDialog,
        tabTitles = listOf(
            stringResource(MR.strings.action_filter),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchPreferenceWidget(
                title = stringResource(TLMR.strings.upcoming_confirmed_airing_only),
                checked = confirmedOnly,
                onCheckedChanged = showConfirmedOnly::set,
            )
            CategoryFilterSheet(viewModel = viewModel)
        }
    }
}
