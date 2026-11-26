package mihon.feature.upcoming.anime

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import mihon.domain.upcoming.anime.interactor.GetUpcomingAnime
import mihon.feature.upcoming.components.UpcomingAgendaDay
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class UpcomingAnimeViewModel(
    private val getUpcomingAnime: GetUpcomingAnime,
    val getCategories: GetAnimeCategories,
    val upcomingPreferences: UpcomingPreferences,
) : ViewModel() {

    val excludedCategories = upcomingPreferences.animeFilterExcludedCategories
    val includedCategories = upcomingPreferences.animeFilterIncludedCategories

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val upcoming = getUpcomingItemPreferenceFlow()
        .distinctUntilChanged()
        .flatMapLatest { prefs ->
            getUpcomingAnime.subscribe(
                excludedCategories = prefs.filterExcludedCategories,
                includedCategories = prefs.filterIncludedCategories,
            )
                .distinctUntilChanged()
                .map { items -> items.toAgenda(prefs.confirmedOnly) }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    val state: StateFlow<State> = combine(
        upcoming,
        dialog,
        getUpcomingItemPreferenceFlow(),
    ) { upcoming, dialog, prefs ->
        State(
            days = upcoming,
            hasActiveFilters = prefs.confirmedOnly ||
                prefs.filterIncludedCategories.isNotEmpty() ||
                prefs.filterExcludedCategories.isNotEmpty(),
            dialog = dialog,
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds),
            State(),
        )

    private fun List<Anime>.toAgenda(confirmedOnly: Boolean): List<UpcomingAgendaDay<UpcomingAnimeUIModel>> {
        val timezone = TimeZone.currentSystemDefault()
        val startOfDay = Clock.System.now().toLocalDateTime(timezone).date.atStartOfDayIn(timezone)
        return mapNotNull { anime ->
            val schedule = upcomingAnimeTime(
                startOfDay,
                anime.nextEpisodeAiringAt,
                anime.nextEpisodeToAir,
                anime.expectedNextUpdate,
            ) ?: return@mapNotNull null
            if (confirmedOnly && schedule.estimated) return@mapNotNull null
            UpcomingAnimeUIModel(anime, schedule)
        }
            .sortedWith(compareBy({ it.schedule.time }, { it.anime.id }))
            .groupBy { it.schedule.time.toLocalDateTime(timezone).date }
            .map { (date, entries) -> UpcomingAgendaDay(date, entries) }
    }

    private fun getUpcomingItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            upcomingPreferences.animeFilterExcludedCategories.changes(),
            upcomingPreferences.animeFilterIncludedCategories.changes(),
            upcomingPreferences.animeConfirmedAiringOnly.changes(),
        ) { excluded, included, confirmedOnly ->
            ItemPreferences(
                filterExcludedCategories = excluded,
                filterIncludedCategories = included,
                confirmedOnly = confirmedOnly,
            )
        }
    }

    fun resetDialog() {
        dialog.update { null }
    }

    fun showFilterDialog() {
        dialog.update { Dialog.FilterSheet }
    }

    fun cycleCategory(category: Category) {
        when (category.id) {
            in includedCategories.get() -> {
                includedCategories.getAndSet { it - category.id }
                excludedCategories.getAndSet { it + category.id }
            }

            in excludedCategories.get() -> excludedCategories.getAndSet { it - category.id }

            else -> includedCategories.getAndSet { it + category.id }
        }
    }

    @Immutable
    private data class ItemPreferences(
        val filterExcludedCategories: List<Long>,
        val filterIncludedCategories: List<Long>,
        val confirmedOnly: Boolean,
    )

    data class State(
        val days: List<UpcomingAgendaDay<UpcomingAnimeUIModel>> = emptyList(),
        val hasActiveFilters: Boolean = false,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object FilterSheet : Dialog
    }
}
