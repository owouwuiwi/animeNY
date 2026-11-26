/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.FilledControlsButton
import eu.kanade.tachiyomi.ui.player.execute
import eu.kanade.tachiyomi.ui.player.executeLongPress
import `is`.xyz.mpv.MPV
import tachiyomi.domain.custombuttons.model.CustomButton

@Composable
fun BottomRightPlayerControls(
    hiddenControls: Set<OptionalPlayerControl>,
    mpv: MPV,
    customButton: CustomButton?,
    customButtonTitle: String,
    skipIntroButton: String?,
    onPressSkipIntroButton: () -> Unit,
    isPipAvailable: Boolean,
    onAspectClick: () -> Unit,
    onPipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Column(horizontalAlignment = Alignment.End) {
            if (skipIntroButton != null) {
                FilledControlsButton(
                    text = skipIntroButton,
                    onClick = onPressSkipIntroButton,
                    onLongClick = {},
                )
            }
            if (customButton != null) {
                FilledControlsButton(
                    text = customButtonTitle,
                    onClick = { customButton.execute(mpv) },
                    onLongClick = { customButton.executeLongPress(mpv) },
                )
            }
        }

        if (isPipAvailable && OptionalPlayerControl.PictureInPicture !in hiddenControls) {
            ControlsButton(
                Icons.Default.PictureInPictureAlt,
                onClick = onPipClick,
            )
        }

        if (OptionalPlayerControl.AspectRatio !in hiddenControls) {
            ControlsButton(
                Icons.Default.AspectRatio,
                onClick = onAspectClick,
            )
        }
    }
}
