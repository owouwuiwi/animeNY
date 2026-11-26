package mihon.feature.upcoming.anime.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ItemCover
import mihon.feature.upcoming.anime.UpcomingAnimeTime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Date

private val UpcomingItemHeight = 96.dp

@Composable
fun UpcomingItem(
    upcoming: Anime,
    schedule: UpcomingAnimeTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(UpcomingItemHeight)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        ItemCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = upcoming.asAnimeCover(),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = upcoming.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(schedule.time.toEpochMilliseconds()))
            val timestamp = when {
                schedule.estimated -> stringResource(TLMR.strings.upcoming_estimated_time, time)
                schedule.episode != null -> stringResource(TLMR.strings.upcoming_episode_time, schedule.episode, time)
                else -> time
            }
            Text(
                text = timestamp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
