// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.browsing.toolbar

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.neeva.app.LocalBrowserToolbarModel
import com.neeva.app.R
import com.neeva.app.ui.PortraitPreviews
import com.neeva.app.ui.TwoBooleanPreviewContainer
import com.neeva.app.ui.theme.Dimensions

@Composable
fun BackButton(
    modifier: Modifier = Modifier
) {
    val browserToolbarModel = LocalBrowserToolbarModel.current
    val navigationInfo by browserToolbarModel.navigationInfoFlow.collectAsState()

    IconButton(
        enabled = navigationInfo.canGoBackward,
        onClick = browserToolbarModel::goBack,
        modifier = modifier
    ) {
        // Material3 chooses "onSurface" as the disabled color and doesn't seem to allow you
        // to change it.  Because we use a similar color for Incognito backgrounds,
        // manually set the color to what we wanted with the alpha they pick to avoid making
        // the button invisible.
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = stringResource(id = R.string.toolbar_go_back),
            tint = LocalContentColor.current.copy(
                alpha = LocalContentColor.current.alpha
            )
        )
    }
}

@Composable
fun ShareButton(modifier: Modifier = Modifier) {
    val browserToolbarModel = LocalBrowserToolbarModel.current

    IconButton(
        onClick = browserToolbarModel::share,
        modifier = modifier
    ) {
        Icon(
            Icons.Default.Share,
            contentDescription = stringResource(id = R.string.share),
            modifier = Modifier.size(Dimensions.SIZE_ICON_TOOLBAR)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeevaScopeButton(
    isLandscape: Boolean = false,
    isIncognito: Boolean = false,
    showRedditDot: MutableState<Boolean> = mutableStateOf(false),
    modifier: Modifier = Modifier
) {
    val browserToolbarModel = LocalBrowserToolbarModel.current
    val iconColor = if (isIncognito) {
        if (isLandscape) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        }
    } else {
        Color.Unspecified
    }

    IconButton(
        onClick = {
            browserToolbarModel.showNeevaScope()
            showRedditDot.value = false
        },
        modifier = modifier
    ) {
        BadgedBox(
            badge = {
                Badge(
                    modifier = Modifier.size(8.dp),
                    containerColor = if (showRedditDot.value) {
                        Color.Blue
                    } else {
                        Color.Transparent
                    }
                ) {}
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_neeva_logo),
                contentDescription = stringResource(id = R.string.neevascope),
                modifier = Modifier.size(Dimensions.SIZE_ICON_TOOLBAR),
                tint = iconColor
            )
        }
    }
}

@Composable
fun AddToSpaceButton(modifier: Modifier = Modifier) {
    val browserToolbarModel = LocalBrowserToolbarModel.current
    val spaceStoreHasUrl by browserToolbarModel.spaceStoreHasUrlFlow.collectAsState()
    val isIncognito = browserToolbarModel.isIncognito
    val shouldDisplayFilled = !isIncognito && spaceStoreHasUrl

    IconButton(
        enabled = !isIncognito,
        onClick = browserToolbarModel::onAddToSpace,
        modifier = modifier.semantics {
            testTag = "IS IN SPACE".takeIf { shouldDisplayFilled } ?: "NOT IN SPACE"
        }
    ) {
        val resourceId = if (shouldDisplayFilled) {
            R.drawable.ic_baseline_bookmark_24
        } else {
            R.drawable.ic_baseline_bookmark_border_24
        }

        Icon(
            painter = painterResource(resourceId),
            contentDescription = stringResource(R.string.toolbar_save_to_space),
            tint = LocalContentColor.current.copy(
                alpha = LocalContentColor.current.alpha
            )
        )
    }
}

@PortraitPreviews
@Composable
fun AddToSpaceButtonPreview() {
    TwoBooleanPreviewContainer { isIncognito, spaceStoreHasUrl ->
        CompositionLocalProvider(
            LocalBrowserToolbarModel provides PreviewBrowserToolbarModel(
                isIncognito = isIncognito,
                spaceStoreHasUrl = spaceStoreHasUrl
            )
        ) {
            Surface {
                AddToSpaceButton()
            }
        }
    }
}

@Composable
fun TabSwitcherButton(modifier: Modifier = Modifier) {
    val browserToolbarModel = LocalBrowserToolbarModel.current

    IconButton(
        onClick = browserToolbarModel::onTabSwitcher,
        modifier = modifier
    ) {
        TabSwitcherIcon(contentDescription = stringResource(R.string.toolbar_tabs_and_spaces))
    }
}
