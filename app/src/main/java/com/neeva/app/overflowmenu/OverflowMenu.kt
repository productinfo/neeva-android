// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.overflowmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neeva.app.R
import com.neeva.app.browsing.toolbar.createBrowserOverflowMenuData
import com.neeva.app.ui.OneBooleanPreviewContainer

@Composable
fun OverflowMenu(
    overflowMenuData: OverflowMenuData,
    onMenuItem: (menuItemId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val showBadge = overflowMenuData.isBadgeVisible

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = modifier
        ) {
            Box {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(id = R.string.toolbar_menu)
                )

                if (showBadge) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }

        // The dropdown's width is arbitrarily set to 250.dp to avoid Compose shrinking the menu to
        // wrap the menu item contents.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.defaultMinSize(minWidth = 250.dp)
        ) {
            OverflowMenuContents(overflowMenuData) { id ->
                onMenuItem(id)
                expanded = false
            }
        }
    }
}

@Preview(name = "LTR", locale = "en")
@Preview(name = "RTL", locale = "he")
@Composable
private fun OverflowMenuPreview() {
    OneBooleanPreviewContainer { isUpdateAvailableVisible ->
        Surface {
            OverflowMenu(
                overflowMenuData = createBrowserOverflowMenuData(
                    isUpdateAvailableVisible = isUpdateAvailableVisible,
                    isForwardEnabled = false, // Not visible -- doesn't matter
                    isDesktopUserAgentEnabled = false // Not visible -- doesn't matter
                ),
                onMenuItem = {}
            )
        }
    }
}
