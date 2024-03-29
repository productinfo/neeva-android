// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.neeva.app.R
import com.neeva.app.ui.OneBooleanPreviewContainer
import com.neeva.app.ui.layouts.BaseRowLayout
import com.neeva.app.ui.theme.Dimensions
import com.neeva.app.ui.widgets.RowActionIconButton
import com.neeva.app.ui.widgets.RowActionIconParams
import com.neeva.app.ui.widgets.RowActionStartIcon
import com.neeva.app.ui.widgets.RowActionStartIconParams

/**
 * Base skeleton for everything that can be displayed as a suggestion in UI.  Callers must provide
 * a Composable [mainContent] that applies the provided modifier in order for it to properly take
 * the fully available width in the row.
 */
@Composable
fun BaseSuggestionRow(
    iconParams: RowActionStartIconParams,
    onTapRow: () -> Unit,
    onTapRowContentDescription: String? = null,
    onLongTap: (() -> Unit)? = null,
    actionIconParams: RowActionIconParams? = null,
    mainContent: @Composable () -> Unit
) {
    BaseRowLayout(
        onTapRow = onTapRow,
        onTapRowContentDescription = onTapRowContentDescription,
        onLongTap = onLongTap,
        startComposable = {
            RowActionStartIcon(iconParams)
        },
        endComposable = actionIconParams?.let { { RowActionIconButton(it) } },
        mainContent = mainContent
    )
}

@Preview("Globe favicon, LTR, 1x", locale = "en")
@Preview("Globe favicon, LTR, 2x", locale = "en", fontScale = 2.0f)
@Preview("Globe favicon, RTL, 1x", locale = "he")
@Composable
fun BaseSuggestionRow_Preview() {
    OneBooleanPreviewContainer { showAction ->
        BaseSuggestionRow(
            onTapRow = {},
            actionIconParams = RowActionIconParams(
                onTapAction = {},
                actionType = RowActionIconParams.ActionType.REFINE,
                contentDescription = stringResource(id = R.string.refine),
            ).takeIf { showAction },
            iconParams = RowActionStartIconParams()
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Magenta)
                    .fillMaxWidth()
                    .height(Dimensions.SIZE_TOUCH_TARGET)
            )
        }
    }
}
