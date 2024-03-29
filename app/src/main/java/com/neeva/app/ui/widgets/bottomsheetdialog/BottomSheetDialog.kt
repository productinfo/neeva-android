// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.ui.widgets.bottomsheetdialog

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neeva.app.R
import com.neeva.app.ui.layouts.BaseRowLayout
import com.neeva.app.ui.theme.Dimensions
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class BottomSheetDialogStates {
    GONE, HALF, FULL
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheetDialog(
    state: SwipeableState<BottomSheetDialogStates>,
    hasHalfwayState: Boolean,
    modifier: Modifier = Modifier,
    @StringRes titleResId: Int? = null,
    onGone: () -> Unit,
    content: @Composable (dismissDialog: () -> Unit) -> Unit
) {
    // Animate the bottom sheet coming in
    var drawScrim by remember { mutableStateOf(false) }
    LaunchedEffect(true) {
        drawScrim = true
        state.animateTo(
            if (hasHalfwayState) {
                BottomSheetDialogStates.HALF
            } else {
                BottomSheetDialogStates.FULL
            }
        )
    }

    // Call onGone when the dialog is GONE so that we can remove it from the Composition.
    var dismissWhenGone by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == BottomSheetDialogStates.GONE) {
            if (dismissWhenGone) onGone()
        } else {
            dismissWhenGone = true
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val dismissDialog: () -> Unit = {
        coroutineScope.launch {
            state.animateTo(BottomSheetDialogStates.GONE)
        }
    }

    val nestedScrollConnection = remember(state) {
        BottomSheetDialogNestedScrollConnection(state)
    }

    BackHandler { dismissDialog() }

    BoxWithConstraints {
        // Set up the anchor points so that the dialog can be expanded to take up the whole screen.
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val anchors: Map<Float, BottomSheetDialogStates> = remember(maxHeightPx) {
            if (hasHalfwayState) {
                mapOf(
                    maxHeightPx to BottomSheetDialogStates.GONE,
                    maxHeightPx / 2 to BottomSheetDialogStates.HALF,
                    0f to BottomSheetDialogStates.FULL
                )
            } else {
                mapOf(
                    maxHeightPx to BottomSheetDialogStates.GONE,
                    0f to BottomSheetDialogStates.FULL
                )
            }
        }

        if (drawScrim) {
            val alpha = (1.0f - state.offset.value / maxHeightPx)
                .coerceAtMost(0.5f).coerceAtLeast(0f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha))
            )
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .swipeable(
                    state = state,
                    anchors = anchors,
                    orientation = Orientation.Vertical,
                )
                .pointerInput(Unit) {
                    // Dismiss the dialog if the user taps outside of it.
                    detectTapGestures { dismissDialog() }
                }
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, state.offset.value.roundToInt()) }
                    .pointerInput(Unit) {
                        // Consume the input so that the parent doesn't try to close the sheet if
                        // the user taps on the dialog directly.
                        detectTapGestures {}
                    }
            ) {
                Column {
                    if (titleResId != null) {
                        BaseRowLayout(
                            endComposable = {
                                IconButton(onClick = dismissDialog) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_baseline_close_24),
                                        contentDescription = stringResource(R.string.close),
                                    )
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(titleResId),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(Dimensions.PADDING_LARGE))
                    }

                    content(dismissDialog)
                }
            }
        }
    }
}
