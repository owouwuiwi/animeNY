package eu.kanade.presentation.more.settings.screen.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.create.BackupCategoryFilter
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class CreateBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<CreateBackupViewModel>()
        val state by viewModel.state.collectAsState()

        val chooseBackupDir = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) {
            if (it != null) {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.createBackup(context, it)
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_create_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.action_create),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    if (!BackupCreateJob.isManualJobRunning(context)) {
                        try {
                            chooseBackupDir.launch(BackupCreator.getFilename())
                        } catch (_: ActivityNotFoundException) {
                            context.toast(MR.strings.file_picker_error)
                        }
                    } else {
                        context.toast(MR.strings.backup_in_progress)
                    }
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item {
                        WarningBanner(MR.strings.restore_miui_warning)
                    }
                }

                item {
                    SectionCard(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, viewModel)
                        LabeledCheckbox(
                            label = stringResource(TLMR.strings.backup_limit_to_categories),
                            checked = state.options.categoryFilter != null,
                            enabled = state.categoryListsLoaded && (state.options.libraryEntries || state.options.categories),
                            onCheckedChange = viewModel::limitToCategories,
                        )
                    }
                }

                state.options.categoryFilter?.let { filter ->
                    item {
                        SectionCard(AYMR.strings.label_anime) {
                            CategoryOptions(state.animeCategories, filter.animeIds) { id, selected ->
                                viewModel.selectCategory(id, isAnime = true, selected = selected)
                            }
                        }
                    }
                    item {
                        SectionCard(AYMR.strings.manga) {
                            CategoryOptions(state.mangaCategories, filter.mangaIds) { id, selected ->
                                viewModel.selectCategory(id, isAnime = false, selected = selected)
                            }
                        }
                    }
                }

                item {
                    SectionCard(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state, viewModel)
                    }
                }

                item {
                    SectionCard(MR.strings.label_extensions) {
                        Options(BackupOptions.extensionOptions, state, viewModel)
                    }
                }
            }
        }
    }

    @Composable
    private fun Options(
        options: List<BackupOptions.Entry>,
        state: CreateBackupViewModel.State,
        model: CreateBackupViewModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
            )
        }
    }

    @Composable
    private fun CategoryOptions(
        categories: List<Category>,
        selectedIds: Set<Long>,
        onSelect: (Long, Boolean) -> Unit,
    ) {
        categories.forEach { category ->
            LabeledCheckbox(
                label = category.visualName,
                checked = category.id in selectedIds,
                onCheckedChange = { selected -> onSelect(category.id, selected) },
            )
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class CreateBackupViewModel(
    animeCategories: GetAnimeCategories,
    mangaCategories: GetMangaCategories,
) : ViewModel() {

    val state: StateFlow<CreateBackupViewModel.State>
        field = MutableStateFlow<CreateBackupViewModel.State>(State())

    init {
        viewModelScope.launch {
            combine(animeCategories.subscribe(), mangaCategories.subscribe(), ::Pair).collect { (anime, manga) ->
                val animeOptions = anime.withDefaultCategory()
                val mangaOptions = manga.withDefaultCategory()
                state.update { current ->
                    val filter = current.options.categoryFilter?.let {
                        BackupCategoryFilter(
                            animeIds = it.animeIds.intersect(animeOptions.map(Category::id).toSet()),
                            mangaIds = it.mangaIds.intersect(mangaOptions.map(Category::id).toSet()),
                        )
                    }
                    current.copy(
                        options = current.options.copy(categoryFilter = filter),
                        animeCategories = animeOptions,
                        mangaCategories = mangaOptions,
                        categoryListsLoaded = true,
                    )
                }
            }
        }
    }

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        state.update {
            val options = setter(it.options, enabled)
            val categoryFilter = options.categoryFilter.takeIf { options.libraryEntries || options.categories }
            it.copy(options = options.copy(categoryFilter = categoryFilter))
        }
    }

    fun limitToCategories(enabled: Boolean) {
        state.update {
            it.copy(options = it.options.copy(categoryFilter = if (enabled) BackupCategoryFilter() else null))
        }
    }

    fun selectCategory(id: Long, isAnime: Boolean, selected: Boolean) {
        state.update {
            val filter = it.options.categoryFilter ?: return@update it
            val ids = if (isAnime) filter.animeIds else filter.mangaIds
            val updatedIds = if (selected) ids + id else ids - id
            val newFilter = if (isAnime) filter.copy(animeIds = updatedIds) else filter.copy(mangaIds = updatedIds)
            it.copy(options = it.options.copy(categoryFilter = newFilter))
        }
    }

    fun createBackup(context: Context, uri: Uri) {
        BackupCreateJob.startNow(context, uri, state.value.options)
    }

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
        val animeCategories: List<Category> = emptyList(),
        val mangaCategories: List<Category> = emptyList(),
        val categoryListsLoaded: Boolean = false,
    )
}

private fun List<Category>.withDefaultCategory(): List<Category> = if (any(Category::isSystemCategory)) {
    this
} else {
    listOf(Category(Category.UNCATEGORIZED_ID, "", 0, 0, false)) + this
}
