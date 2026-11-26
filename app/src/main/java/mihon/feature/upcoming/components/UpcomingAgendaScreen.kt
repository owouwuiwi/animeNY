package mihon.feature.upcoming.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.core.os.ConfigurationCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.core.common.Constants
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import mihon.feature.upcoming.components.calendar.AgendaPeriod
import mihon.feature.upcoming.components.calendar.Calendar
import mihon.feature.upcoming.components.calendar.CalendarDay
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Help
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.KeyboardArrowLeft
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Clock
import java.time.LocalDate as JavaLocalDate

@Composable
internal fun <T> UpcomingAgendaScreen(
    title: String,
    days: List<UpcomingAgendaDay<T>>,
    hasActiveFilters: Boolean,
    onFilterClick: () -> Unit,
    entryKey: (T) -> Long,
    entryContent: @Composable (T) -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val uriHandler = LocalUriHandler.current
    val timezone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timezone).date
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    var anchor by rememberSaveable { mutableStateOf(today.toString()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var monthView by rememberSaveable { mutableStateOf(false) }
    val period = if (monthView) AgendaPeriod.Month else AgendaPeriod.Week
    val anchorDate = JavaLocalDate.parse(anchor)
    val range = period.dates(anchorDate, WeekFields.of(locale).firstDayOfWeek)
    val events = remember(days) { days.associate { it.date to it.entries.size } }
    val visibleDays = remember(days, range, selected) {
        days.filter { it.date.toJavaLocalDate() in range && (selected == null || it.date.toString() == selected) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(anchor, period, selected) {
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
    }

    val onDayClick: (LocalDate) -> Unit = { date ->
        val value = date.toString()
        selected = value.takeUnless { it == selected }
    }
    val calendar: @Composable () -> Unit = {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !monthView,
                    onClick = {
                        selected?.let { anchor = it }
                        monthView = false
                    },
                    label = { Text(stringResource(TLMR.strings.upcoming_view_week)) },
                )
                FilterChip(
                    selected = monthView,
                    onClick = {
                        selected?.let { anchor = it }
                        monthView = true
                    },
                    label = { Text(stringResource(TLMR.strings.upcoming_view_month)) },
                )
                TextButton(
                    onClick = {
                        anchor = today.toString()
                        selected = anchor
                    },
                ) {
                    Text(stringResource(MR.strings.relative_time_today))
                }
            }

            if (monthView) {
                Calendar(
                    selectedYearMonth = anchorDate.toKotlinLocalDate().yearMonth,
                    events = events,
                    setSelectedYearMonth = {
                        anchor = it.firstDay.toString()
                        selected = null
                    },
                    onClickDay = onDayClick,
                    selectedDate = selected?.let(LocalDate::parse),
                )
            } else {
                WeeklyCalendar(
                    firstDay = range.start,
                    timezone = timezone,
                    locale = locale,
                    events = events,
                    selected = selected,
                    onDayClick = onDayClick,
                    onMoveWeek = { weeks ->
                        anchor = anchorDate.plusWeeks(weeks).toString()
                        selected = null
                    },
                )
            }
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = title,
                navigateUp = navigator::pop,
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_filter),
                                icon = MaterialSymbols.Rounded.FilterList,
                                iconTint = if (hasActiveFilters) {
                                    MaterialTheme.colorScheme.active
                                } else {
                                    LocalContentColor.current
                                },
                                onClick = onFilterClick,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.upcoming_guide),
                                icon = MaterialSymbols.AutoMirroredRounded.Help,
                                onClick = { uriHandler.openUri(Constants.URL_HELP_UPCOMING) },
                            ),
                        ),
                    )
                },
            )
        },
    ) { padding ->
        if (isTabletUi()) {
            TwoPanelBox(
                modifier = Modifier.padding(padding),
                startContent = { calendar() },
                endContent = {
                    FastScrollLazyColumn(state = listState) {
                        agendaEntries(visibleDays, entryKey, entryContent)
                    }
                },
            )
        } else {
            FastScrollLazyColumn(contentPadding = padding, state = listState) {
                item(key = "upcoming-calendar") { calendar() }
                agendaEntries(visibleDays, entryKey, entryContent)
            }
        }
    }
}

@Composable
private fun WeeklyCalendar(
    firstDay: JavaLocalDate,
    timezone: TimeZone,
    locale: Locale,
    events: Map<LocalDate, Int>,
    selected: String?,
    onDayClick: (LocalDate) -> Unit,
    onMoveWeek: (Long) -> Unit,
) {
    fun JavaLocalDate.startMillis() = toKotlinLocalDate().atStartOfDayIn(timezone).toEpochMilliseconds()

    val title = DateUtils.formatDateRange(
        LocalContext.current,
        firstDay.startMillis(),
        firstDay.plusDays(7).startMillis() - 1L,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMoveWeek(-1) }) {
            @Suppress("DEPRECATION")
            Icon(MaterialSymbols.Rounded.KeyboardArrowLeft, stringResource(TLMR.strings.upcoming_week_previous))
        }
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onMoveWeek(1) }) {
            @Suppress("DEPRECATION")
            Icon(MaterialSymbols.Rounded.KeyboardArrowRight, stringResource(TLMR.strings.upcoming_week_next))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { index ->
            val date = firstDay.plusDays(index.toLong())
            val localDate = date.toKotlinLocalDate()
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                )
                CalendarDay(
                    date = localDate,
                    events = events[localDate] ?: 0,
                    onDayClick = { onDayClick(localDate) },
                    selected = selected == localDate.toString(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun <T> LazyListScope.agendaEntries(
    days: List<UpcomingAgendaDay<T>>,
    entryKey: (T) -> Long,
    entryContent: @Composable (T) -> Unit,
) {
    if (days.isEmpty()) {
        item(key = "upcoming-empty") {
            Text(
                text = stringResource(TLMR.strings.upcoming_empty_period),
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    for ((date, entries) in days) {
        item(key = "upcoming-day-$date", contentType = "header") {
            Text(
                text = relativeDateText(date),
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(items = entries, key = entryKey, contentType = { "entry" }) { entryContent(it) }
    }
}
