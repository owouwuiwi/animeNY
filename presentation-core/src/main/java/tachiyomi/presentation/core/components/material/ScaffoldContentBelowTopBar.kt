package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Positions the content viewport below the app bar so directional focus can enter the content.
 * The remaining scaffold padding is still available for insets inside the viewport.
 */
@Composable
fun ScaffoldContentBelowTopBar(
    scaffoldPadding: PaddingValues,
    content: @Composable (PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
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
