package eu.kanade.presentation.more.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.stableLazyKeys
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import kotlin.time.Duration.Companion.seconds

/**
 * Preference Screen composable which contains a list of [Preference] items
 * @param items [Preference] items which should be displayed on the preference screen. An item can be a single [PreferenceItem] or a group ([Preference.PreferenceGroup])
 * @param modifier [Modifier] to be applied to the preferenceScreen layout
 */
@Composable
fun PreferenceScreen(
    items: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state = rememberLazyListState()
    val rows = remember(items) { items.toVisibleRows() }
    val highlightKey = SearchableSettings.highlightKey
    if (highlightKey != null) {
        val index = rows.indexOfFirst { (it.preference as? Preference.PreferenceItem<*>)?.title == highlightKey }
        LaunchedEffect(highlightKey, index) {
            if (index >= 0) {
                delay(0.5.seconds)
                state.animateScrollToItem(index)
            }
            if (SearchableSettings.highlightKey == highlightKey) {
                SearchableSettings.highlightKey = null
            }
        }
    }

    ScrollbarLazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
    ) {
        items(rows, key = PreferenceRow::key) { row ->
            when (val preference = row.preference) {
                is Preference.PreferenceGroup -> PreferenceGroupHeader(title = preference.title)
                is Preference.PreferenceItem<*> -> {
                    PreferenceItem(
                        item = preference,
                        highlightKey = highlightKey,
                    )
                }
                null -> Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

internal data class PreferenceRow(val key: String, val preference: Preference?)

internal fun List<Preference>.toVisibleRows(): List<PreferenceRow> {
    val keys = stableLazyKeys(Preference::rowIdentity)
    val visible = mapIndexedNotNull { index, preference ->
        if (preference is Preference.PreferenceGroup && !preference.enabled) null else keys[index] to preference
    }
    return buildList {
        visible.forEachIndexed { index, (key, preference) ->
            when (preference) {
                is Preference.PreferenceGroup -> {
                    add(PreferenceRow("header:$key", preference))
                    val childKeys = preference.preferenceItems.stableLazyKeys(Preference::rowIdentity)
                    preference.preferenceItems.forEachIndexed { childIndex, child ->
                        add(PreferenceRow("child:${key.length}:$key:${childKeys[childIndex]}", child))
                    }
                    if (index < visible.lastIndex) add(PreferenceRow("spacer:$key", null))
                }

                is Preference.PreferenceItem<*> -> add(PreferenceRow("row:$key", preference))
            }
        }
    }
}

private fun Preference.rowIdentity(): String = "${javaClass.name}:$title"
