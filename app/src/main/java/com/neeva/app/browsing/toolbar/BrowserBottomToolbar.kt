// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.browsing.toolbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.neeva.app.LocalBrowserToolbarModel
import com.neeva.app.LocalBrowserWrapper
import com.neeva.app.R
import com.neeva.app.browsing.ActiveTabModel
import com.neeva.app.neevascope.NeevaScopeTooltip
import com.neeva.app.ui.OneBooleanPreviewContainer
import com.neeva.app.ui.PortraitPreviews

/** Contains all the controls available to the user in the bottom toolbar. */
@Composable
fun BrowserBottomToolbar(
    bottomOffset: Float,
    modifier: Modifier = Modifier
) {
    val browserWrapper = LocalBrowserWrapper.current
    val bottomOffsetDp = with(LocalDensity.current) { bottomOffset.toDp() }

    BrowserBottomToolbar(
        isIncognito = browserWrapper.isIncognito,
        modifier = modifier.offset(y = bottomOffsetDp)
    )
}

@Composable
fun BrowserBottomToolbar(
    isIncognito: Boolean,
    modifier: Modifier = Modifier
) {
    val browserToolbarModel = LocalBrowserToolbarModel.current
    val showRedditDot = remember { mutableStateOf(false) }

    val backgroundColor = if (isIncognito) {
        MaterialTheme.colorScheme.inverseSurface
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        color = backgroundColor,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.bottom_toolbar_height))
            .semantics { testTag = "BrowserBottomToolbar" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            BackButton(
                modifier = Modifier.weight(1.0f)
            )

            ShareButton(
                modifier = Modifier.weight(1.0f)
            )

            if (browserToolbarModel.shouldShowNeevaScopeTooltip()) {
                browserToolbarModel.getNeevaScopeModel()?.let {
                    NeevaScopeTooltip(
                        neevaScopeModel = it,
                        showRedditDot = showRedditDot,
                        isLandscape = false
                    )
                }
            }

            NeevaScopeButton(
                isIncognito = isIncognito,
                showRedditDot = showRedditDot,
                modifier = Modifier.weight(1.0f)
            )

            AddToSpaceButton(
                modifier = Modifier.weight(1.0f)
            )

            TabSwitcherButton(
                modifier = Modifier.weight(1.0f)
            )
        }
    }
}

@PortraitPreviews
@Composable
fun BottomToolbarPreview_Regular() {
    OneBooleanPreviewContainer { isIncognito ->
        CompositionLocalProvider(
            LocalBrowserToolbarModel provides PreviewBrowserToolbarModel(
                isIncognito = isIncognito
            )
        ) {
            BrowserBottomToolbar(isIncognito = isIncognito)
        }
    }
}

@PortraitPreviews
@Composable
fun BottomToolbarPreview_CanGoBackward() {
    OneBooleanPreviewContainer { isIncognito ->
        CompositionLocalProvider(
            LocalBrowserToolbarModel provides PreviewBrowserToolbarModel(
                navigationInfo = ActiveTabModel.NavigationInfo(
                    canGoBackward = true
                ),
                spaceStoreHasUrl = false,
                isIncognito = isIncognito
            )
        ) {
            BrowserBottomToolbar(isIncognito = isIncognito)
        }
    }
}

@PortraitPreviews
@Composable
fun BottomToolbarPreview_SpaceStoreHasUrl() {
    OneBooleanPreviewContainer { isIncognito ->
        CompositionLocalProvider(
            LocalBrowserToolbarModel provides PreviewBrowserToolbarModel(
                navigationInfo = ActiveTabModel.NavigationInfo(canGoBackward = false),
                spaceStoreHasUrl = true,
                isIncognito = isIncognito
            )
        ) {
            BrowserBottomToolbar(isIncognito = isIncognito)
        }
    }
}
