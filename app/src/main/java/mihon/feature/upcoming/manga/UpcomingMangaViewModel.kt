package mihon.feature.upcoming.manga

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
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import mihon.feature.upcoming.components.UpcomingAgendaDay
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class UpcomingMangaViewModel(
    private val getUpcomingManga: GetUpcomingManga,
    val getCategories: GetMangaCategories,
    val upcomingPreferences: UpcomingPreferences,
) : ViewModel() {

    val excludedCategories = upcomingPreferences.mangaFilterExcludedCategories
    val includedCategories = upcomingPreferences.mangaFilterIncludedCategories

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val hasActiveFilters = getUpcomingItemPreferenceFlow()
        .map { prefs ->
            listOf(
                prefs.filterIncludedCategories,
                prefs.filterExcludedCategories,
            )
                .any { it.isNotEmpty() }
        }
        .distinctUntilChanged()

    private val upcoming = getUpcomingItemPreferenceFlow()
        .distinctUntilChanged()
        .flatMapLatest { prefs ->
            getUpcomingManga.subscribe(
                excludedCategories = prefs.filterExcludedCategories,
                includedCategories = prefs.filterIncludedCategories,
            )
                .distinctUntilChanged()
                .map { items -> items.toAgenda() }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    val state: StateFlow<State> = combine(
        upcoming,
        dialog,
        hasActiveFilters,
    ) { upcoming, dialog, hasActiveFilters ->
        State(
            days = upcoming,
            hasActiveFilters = hasActiveFilters,
            dialog = dialog,
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds),
            State(),
        )

    private fun List<Manga>.toAgenda(): List<UpcomingAgendaDay<Manga>> {
        val timezone = TimeZone.currentSystemDefault()
        val startOfDay = Clock.System.now().toLocalDateTime(timezone).date.atStartOfDayIn(timezone)
        return mapNotNull { manga ->
            val expected = manga.expectedNextUpdate?.takeIf { it >= startOfDay } ?: return@mapNotNull null
            expected to manga
        }
            .sortedWith(compareBy({ it.first }, { it.second.id }))
            .groupBy({ (expected, _) -> expected.toLocalDateTime(timezone).date }, { (_, manga) -> manga })
            .map { (date, entries) -> UpcomingAgendaDay(date, entries) }
    }

    private fun getUpcomingItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            upcomingPreferences.mangaFilterExcludedCategories.changes(),
            upcomingPreferences.mangaFilterIncludedCategories.changes(),
        ) { excluded, included ->
            ItemPreferences(
                filterExcludedCategories = excluded,
                filterIncludedCategories = included,
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
    )

    data class State(
        val days: List<UpcomingAgendaDay<Manga>> = emptyList(),
        val hasActiveFilters: Boolean = false,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object FilterSheet : Dialog
    }
}
