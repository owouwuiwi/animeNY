package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
internal fun SourceResultsViewport(
    scaffoldPadding: PaddingValues,
    content: @Composable (PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    // Give the lazy focus group bounds below the toolbar so D-pad Down can enter it.
    Box(Modifier.padding(top = scaffoldPadding.calculateTopPadding())) {
        content(
            PaddingValues(
                start = scaffoldPadding.calculateStartPadding(layoutDirection),
                end = scaffoldPadding.calculateEndPadding(layoutDirection),
                bottom = scaffoldPadding.calculateBottomPadding(),
            ),
        )
    }
}
